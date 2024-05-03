package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.DirDataDTO;
import net.vjdv.filecalli.dto.FileDataDTO;
import net.vjdv.filecalli.dto.RetrievedFileDTO;
import net.vjdv.filecalli.dto.SessionDTO;
import net.vjdv.filecalli.exceptions.ResourceNotFoundException;
import net.vjdv.filecalli.exceptions.StorageException;
import net.vjdv.filecalli.util.Configuration;
import net.vjdv.filecalli.util.CryptHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class StorageService {

    private final DataService dataService;
    private final TasksService tasksService;
    private final Configuration config;

    public StorageService(DataService dataService, TasksService tasksService, Configuration configuration) {
        this.dataService = dataService;
        this.tasksService = tasksService;
        this.config = configuration;
    }

    /**
     * Lists the contents of a directory
     *
     * @param path    directory path
     * @param rootDir user root directory
     * @return list of directories and files
     */
    public List<ListedResource> list(String path, int rootDir) {
        int directoryId = resolveDir(path, rootDir, true).id();
        // directories
        String sql1 = "SELECT id, name FROM directories WHERE parent = ?";
        List<ListedResource> dirs = dataService.queryList(sql1, rs -> new ListedResource(rs.getString(2), true, false, 0, 0, 0), directoryId);
        // files
        String sql2 = "SELECT id, name, size, created_at, last_modified FROM files WHERE directory_id = ?";
        List<ListedResource> files = dataService.queryList(sql2, rs -> {
            String name = rs.getString("name");
            int size = rs.getInt("size");
            long createdAt = rs.getLong("created_at");
            long lastModified = rs.getLong("last_modified");
            return new ListedResource(name, false, true, size, createdAt, lastModified);
        }, directoryId);
        //return
        List<ListedResource> resources = new ArrayList<>();
        resources.addAll(dirs);
        resources.addAll(files);
        return resources;
    }

    /**
     * Creates a directory in the storage
     *
     * @param path    directory path
     * @param session user session
     * @return the directory id
     */
    public int createDirectory(String path, SessionDTO session) {
        if ("/".equals(path)) throw new StorageException("Invalid directory name");
        if (!path.startsWith("/")) throw new StorageException("Path must start with /");
        if (path.endsWith("/")) throw new StorageException("Directory name must not end with /");
        long now = Instant.now().toEpochMilli();
        var dataDir = resolveDir(path, session.rootDir(), false);
        if (dataDir.id() != 0) {
            throw new StorageException("Directory already exists");
        }
        String sql = "INSERT INTO directories (name, parent, created_at, last_modified) VALUES (?, ?, ?, ?)";
        return dataService.insertAutoincrement(sql, dataDir.name(), dataDir.parentId(), now, now);
    }

    /**
     * Stores a file in the storage
     *
     * @param filePath      user's file path
     * @param multipartFile file to store
     * @param session       user's session
     */

    public void store(String filePath, MultipartFile multipartFile, SessionDTO session) {
        try {
            store(filePath, multipartFile.getContentType(), multipartFile.getSize(), multipartFile.getInputStream(), session);
        } catch (IOException ex) {
            throw new StorageException("Error getting inputStream from multipartFile", ex);
        }
    }

    /**
     * Stores a file in the storage
     *
     * @param filePath user's file path
     * @param mime     file mime type
     * @param size     file size
     * @param input    file input stream
     * @param session  user's session
     */
    public void store(String filePath, String mime, long size, InputStream input, SessionDTO session) {
        //Resolves dirId and fileId
        var data1 = resolveFile(filePath, session.rootDir());
        int dirId = data1.directoryId();
        int idFile = data1.id();
        //some data
        long now = Instant.now().toEpochMilli();
        //insert row if file does not exist
        if (idFile == 0) {
            String sql = "INSERT INTO files (name, mime, size, directory_id, created_at, last_modified) VALUES (?, ?, 0, ?, ?, 0)";
            idFile = dataService.insertAutoincrement(sql, data1.name(), mime, dirId, now);
        }
        log.info("Storing {} file id={}", data1.id() == 0 ? "new" : "existing", idFile);
        Path fileDestPath = computeFilePath(idFile);
        Path parent = fileDestPath.getParent();
        //parent directory must exist
        if (!Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ex) {
                throw new StorageException("Error creating directories", ex);
            }
        }
        //store the file
        SecretKey key = session.key();
        if (filePath.startsWith("/webdav/")) key = session.webdavKey();
        try {
            CryptHelper.encrypt(input, fileDestPath, key);
        } catch (IOException ex) {
            throw new StorageException("Error storing file", ex);
        }
        //update the file size
        String sql = "UPDATE files SET size = ?, last_modified = ? WHERE id = ?";
        dataService.update(sql, size, now, idFile);
    }

    /**
     * Retrieves a file from the storage
     *
     * @param filePath user's file path
     * @param session  user's session
     * @return file data like id and size
     * @throws ResourceNotFoundException if the file does not exist
     */
    public RetrievedFileDTO retrieve(String filePath, SessionDTO session) {
        var data = resolveFile(filePath, session.rootDir());
        if (data.id() == 0) throw new ResourceNotFoundException("File " + filePath + " does not exist");
        Path inputFile = computeFilePath(data.id());
        SecretKey key = session.key();
        if (filePath.startsWith("/webdav/")) key = session.webdavKey();
        try (var inputStream = Files.newInputStream(inputFile)) {
            var outputPath = tasksService.getTempFile();
            CryptHelper.decrypt(inputStream, outputPath, key);
            return new RetrievedFileDTO(data, outputPath);
        } catch (IOException ex) {
            throw new StorageException("Error retrieving file", ex);
        }
    }

    /**
     * Deletes a file from the storage
     *
     * @param filePath user's file path
     * @param rootDir  user's root directory
     * @throws ResourceNotFoundException if the file does not exist
     */
    public void delete(String filePath, int rootDir) {
        var data = resolveFile(filePath, rootDir);
        if (data.id() == 0) throw new ResourceNotFoundException("File " + filePath + " does not exist");
        Path inputFile = computeFilePath(data.id());
        try {
            if (Files.exists(inputFile)) Files.delete(inputFile);
        } catch (IOException ex) {
            throw new StorageException("Error deleting file", ex);
        }
        String sql = "DELETE FROM files WHERE id = ?";
        dataService.update(sql, data.id());
    }

    /**
     * Deletes a directory from the storage
     *
     * @param path               directory path
     * @param deleteWithContents if true, deletes the directory and its contents
     * @param rootDir            user's root directory
     * @return number of files deleted
     * @throws StorageException if the directory is not empty and deleteWithContents is false
     */
    public int deleteDirectory(String path, boolean deleteWithContents, int rootDir) {
        if ("/".equals(path)) throw new StorageException("Cannot delete root directory");
        int dirId = resolveDir(path, rootDir, true).id();
        //validates if the directory is empty
        if (!deleteWithContents) {
            String sql = "SELECT COUNT(1) FROM directories, files WHERE directories.parent = ? OR ( files.directory_id = ? AND files.directory_id = directories.id )";
            int count = dataService.queryOne(sql, rs -> rs.getInt(1), dirId, dirId).orElse(0);
            if (count > 0) throw new StorageException("Directory is not empty");
        }
        int deletedFiles = 0;
        //delete directories
        String sql1 = "SELECT name FROM directories WHERE parent = ?";
        List<String> dirs = dataService.queryList(sql1, rs -> rs.getString(1), dirId);
        for (String dir : dirs) {
            deletedFiles += deleteDirectory(path + "/" + dir, true, rootDir);
        }
        //delete files
        String sql2 = "SELECT name FROM files WHERE directory_id = ?";
        List<String> files = dataService.queryList(sql2, rs -> rs.getString(1), dirId);
        for (String file : files) {
            delete(path + "/" + file, rootDir);
            deletedFiles++;
        }
        //delete directory
        String sql3 = "DELETE FROM directories WHERE id = ?";
        dataService.update(sql3, dirId);
        //return
        return deletedFiles;
    }

    /**
     * Moves a file to a new location
     *
     * @param src     source file path
     * @param dest    destination file path
     * @param rootDir user root directory
     */
    public void moveFile(String src, String dest, int rootDir) {
        var srcData = resolveFile(src, rootDir);
        if (srcData.id() == 0) throw new ResourceNotFoundException("File " + src + " does not exist");
        var destData = resolveFile(dest, rootDir);
        if (destData.id() != 0) throw new StorageException("File " + dest + " already exists");
        //update sql
        String sql = "UPDATE files SET directory_id = ?, name = ? WHERE id = ?";
        dataService.update(sql, destData.directoryId(), destData.name(), srcData.id());
    }

    /**
     * Moves a directory to a new location
     *
     * @param src     source directory path
     * @param dest    destination directory path
     * @param rootDir user root directory
     */
    public void moveDirectory(String src, String dest, int rootDir) {
        var srcData = resolveDir(src, rootDir, true);
        var destData = resolveDir(dest, rootDir, false);
        if (destData.id() != 0) throw new StorageException("Directory " + dest + " already exists");
        //update sql
        String sql = "UPDATE directories SET parent = ?, name = ? WHERE id = ?";
        dataService.update(sql, destData.parentId(), destData.name(), srcData.id());
    }

    public void copyFile(String src, String dest, SessionDTO session) {
        var srcData = resolveFile(src, session.rootDir());
        if (srcData.id() == 0) throw new ResourceNotFoundException("File " + src + " does not exist");
        var destData = resolveFile(dest, session.rootDir());
        if (destData.id() != 0) throw new StorageException("File " + dest + " already exists");
        //insert
        long now = Instant.now().toEpochMilli();
        String sql = "INSERT INTO files (name, mime, size, directory_id, created_at, last_modified) VALUES (?, ?, 0, ?, ?, 0)";
        int idFile = dataService.insertAutoincrement(sql, destData.name(), srcData.mime(), destData.directoryId(), now);
        log.info("Storing copy file {} id={}", dest, idFile);
        Path fileDestPath = computeFilePath(idFile);
        Path parent = fileDestPath.getParent();
        //parent directory must exist
        if (!Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ex) {
                throw new StorageException("Error creating directories", ex);
            }
        }
        //copy the file
        Path fileSrcPath = computeFilePath(srcData.id());
        if ((src.startsWith("/webdav/") && dest.startsWith("/webdav/")) || (!src.startsWith("/webdav/") && !dest.startsWith("/webdav/"))) {
            try {
                Files.copy(fileSrcPath, fileDestPath);
            } catch (IOException ex) {
                throw new StorageException("Error copying file", ex);
            }
        } else {
            SecretKey decodeKey = session.key();
            SecretKey encodeKey = session.key();
            if (src.startsWith("/webdav/")) decodeKey = session.webdavKey();
            if (dest.startsWith("/webdav/")) encodeKey = session.webdavKey();
            try (var inputStream = Files.newInputStream(fileSrcPath)) {
                var tempPath = tasksService.getTempFile();
                CryptHelper.decrypt(inputStream, tempPath, decodeKey);
                try (var decodedStream = Files.newInputStream(tempPath)) {
                    CryptHelper.encrypt(decodedStream, fileDestPath, encodeKey);
                }
            } catch (IOException ex) {
                throw new StorageException("Error copying file", ex);
            }
        }
        //update the file size
        String sql2 = "UPDATE files SET size = ?, last_modified = ? WHERE id = ?";
        int updated = dataService.update(sql2, srcData.size(), now, idFile);
        if (updated != 1) throw new StorageException("Error updating file size id=" + idFile + " updated=" + updated);
    }

    public void copyDirectory(String src, String dest, SessionDTO session) {
        var srcData = resolveDir(src, session.rootDir(), true);
        var destData = resolveDir(dest, session.rootDir(), false);
        if (destData.id() != 0) throw new StorageException("Directory " + dest + " already exists");
        //insert
        long now = Instant.now().toEpochMilli();
        String sql = "INSERT INTO directories (name, parent, created_at, last_modified) VALUES (?, ?, ?, ?)";
        dataService.insertAutoincrement(sql, destData.name(), destData.parentId(), now, now);
        log.info("Copying directory {} to {}", src, dest);
        //copy the directory
        String sql2 = "SELECT name FROM directories WHERE parent = ?";
        List<String> dirs = dataService.queryList(sql2, rs -> rs.getString(1), srcData.id());
        for (String dir : dirs) {
            copyDirectory(src + "/" + dir, dest + "/" + dir, session);
        }
        String sql3 = "SELECT name FROM files WHERE directory_id = ?";
        List<String> files = dataService.queryList(sql3, rs -> rs.getString(1), srcData.id());
        for (String file : files) {
            copyFile(src + "/" + file, dest + "/" + file, session);
        }
    }

    /**
     * Resolves the directory id from the path
     *
     * @param path    user directory path
     * @param rootDir user root directory
     * @return directory id
     * @throws ResourceNotFoundException if the path does not exist
     */
    protected DirDataDTO resolveDir(String path, int rootDir, boolean mustExists) {
        //root directory
        String sql = "SELECT created_at, last_modified FROM directories WHERE id = ?";
        var dirData = dataService.queryOne(sql, rs -> {
            long createdAt = rs.getLong(1);
            long lastModified = rs.getLong(2);
            return new DirDataDTO(rootDir, "/", "/", createdAt, lastModified, 0);
        }, rootDir).orElseThrow(() -> new ResourceNotFoundException("Root directory not found"));
        if ("/".equals(path)) return dirData;
        //validation
        if (!path.startsWith("/")) throw new ResourceNotFoundException("Path must start with /");
        if (path.endsWith("/")) throw new ResourceNotFoundException("Directory name must not end with /");
        //starts resolving
        String[] paths = path.substring(1).split("/");
        StringBuilder resolvedPath = new StringBuilder();
        for (String name : paths) {
            if ("".equals(name)) throw new ResourceNotFoundException("Invalid path");
            resolvedPath.append("/").append(name);
            String sql2 = "SELECT id, created_at, last_modified FROM directories WHERE name = ? AND parent = ?";
            int lastDir = dirData.id();
            dirData = dataService.queryOne(sql2, rs -> {
                int id = rs.getInt(1);
                long createdAt = rs.getLong(2);
                long lastModified = rs.getLong(3);
                return new DirDataDTO(id, name, resolvedPath.toString(), createdAt, lastModified, lastDir);
            }, name, lastDir).orElse(new DirDataDTO(0, name, resolvedPath.toString(), 0, 0, lastDir));
            if (dirData.id() == 0) break;
        }
        if (!dirData.path().equals(path)) {
            throw new ResourceNotFoundException("Some parent of " + path + " does not exist");
        }
        if (mustExists && dirData.id() == 0) {
            throw new ResourceNotFoundException("Directory " + path + " does not exist");
        }
        return dirData;
    }

    /**
     * Resolves the file id from the path
     *
     * @param path    user file path
     * @param rootDir user root directory
     * @return file data, if the file does not exist, the fileId is 0
     * @throws ResourceNotFoundException if the directory does not exist
     */
    protected FileDataDTO resolveFile(String path, int rootDir) {
        if ("/".equals(path)) throw new ResourceNotFoundException("Invalid file path");
        if (!path.startsWith("/")) throw new ResourceNotFoundException("Path must start with /");
        int slashIndex = path.lastIndexOf("/");
        String dirPath = path.substring(0, slashIndex);
        String fileName = path.substring(slashIndex + 1);
        var dirData = resolveDir(dirPath, rootDir, true);
        String sql = "SELECT id, name, mime, size, created_at, last_modified FROM files WHERE name = ? AND directory_id = ?";
        var fileData = dataService.queryOne(sql, rs -> {
            int id = rs.getInt(1);
            String name = rs.getString(2);
            String mime = rs.getString(3);
            long size = rs.getLong(4);
            long createdAt = rs.getLong(5);
            long lastModified = rs.getLong(6);
            return new FileDataDTO(id, name, dirData.path() + "/" + name, mime, size, createdAt, lastModified, dirData.id());
        }, fileName, dirData.id());
        return fileData.orElseGet(() -> new FileDataDTO(0, fileName, dirData.path() + "/" + fileName, "", 0, 0, 0, dirData.id()));
    }

    /**
     * Directory and filename of encrypted file depends on the fileId
     *
     * @param fileId the file id
     * @return path of encrypted file
     */
    private Path computeFilePath(int fileId) {
        StringBuilder fileName = new StringBuilder(Integer.toHexString(fileId % 1000));
        while (fileName.length() < 3) {
            fileName.insert(0, "0");
        }
        return config.getDataPath().resolve(Integer.toHexString(fileId / 1000 + 160)).resolve(fileName.toString());
    }

    public record ListedResource(String name, boolean isDirectory, boolean isRegularFile, int size, long createdAt,
                                 long lastModified) {
    }

}
