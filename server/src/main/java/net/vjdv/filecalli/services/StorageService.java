package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
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
        int directoryId = resolveDir(path, rootDir);
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
        int slashIndex = path.lastIndexOf("/");
        String dirPath = path.substring(0, slashIndex + 1);
        String dirName = path.substring(slashIndex + 1);
        int parentDir = resolveDir(dirPath, session.rootDir());
        String sql = "INSERT INTO directories (name, parent, created_at, last_modified) VALUES (?, ?, ?, ?, ?)";
        return dataService.insertAutoincrement(sql, dirName, parentDir, now, now);
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
            } catch (Exception ex) {
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
     * Resolves the directory id from the path
     *
     * @param path    user directory path
     * @param rootDir user root directory
     * @return directory id
     * @throws ResourceNotFoundException if the path does not exist
     */
    protected int resolveDir(String path, int rootDir) {
        if ("/".equals(path)) return rootDir;
        if (!path.startsWith("/")) throw new ResourceNotFoundException("Path must start with /");
        String[] paths = path.substring(1).split("/");
        int lastDir = 0;
        for (int i = 0; i < paths.length; i++) {
            if ("".equals(paths[i])) throw new ResourceNotFoundException("Invalid path");
            String sql = "SELECT id FROM directories WHERE name = ? AND parent = ?";
            int parent = i == 0 ? rootDir : lastDir;
            lastDir = dataService.queryOne(sql, rs -> rs.getInt(1), paths[i], parent).orElseThrow(() -> new ResourceNotFoundException("Path " + path + " not found"));
        }
        return lastDir;
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
        String dirPath = path.substring(0, slashIndex + 1);
        String fileName = path.substring(slashIndex + 1);
        int dirId = resolveDir(dirPath, rootDir);
        String sql = "SELECT id, name, mime, size, created_at, last_modified FROM files WHERE name = ? AND directory_id = ?";
        var fileData = dataService.queryOne(sql, rs -> {
            int id = rs.getInt(1);
            String name = rs.getString(2);
            String mime = rs.getString(3);
            long size = rs.getLong(4);
            long createdAt = rs.getLong(5);
            long lastModified = rs.getLong(6);
            return new FileDataDTO(id, name, mime, size, createdAt, lastModified, dirId);
        }, fileName, dirId);
        return fileData.orElseGet(() -> new FileDataDTO(0, fileName, "", 0, 0, 0, dirId));
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
