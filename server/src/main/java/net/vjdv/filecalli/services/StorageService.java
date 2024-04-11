package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.SessionDTO;
import net.vjdv.filecalli.exceptions.ResourceNotFoundException;
import net.vjdv.filecalli.exceptions.StorageException;
import net.vjdv.filecalli.util.Configuration;
import net.vjdv.filecalli.util.CryptHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class StorageService {

    private final DataService dataService;
    private final Configuration config;

    public StorageService(DataService dataService, Configuration configuration) {
        this.dataService = dataService;
        this.config = configuration;
    }

    /**
     * Stores a file in the storage
     *
     * @param filePath      user's file path
     * @param multipartFile file to store
     * @param session       user's session
     */

    public void store(String filePath, MultipartFile multipartFile, SessionDTO session) {
        //Resolves dirId and fileId
        var data1 = resolveFile(filePath, session.rootDir());
        int dirId = data1.dirId;
        int idFile = data1.fileId;
        //Get info from db
        if (idFile == 0) {
            String sql = "INSERT INTO files (name, size, directory_id) VALUES (?, 0, ?)";
            idFile = dataService.insertAutoincrement(sql, data1.fileName, dirId);
        }
        log.info("Storing {} file id={}", data1.fileId == 0 ? "new" : "existing", idFile);
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
        try {
            CryptHelper.encrypt(multipartFile.getInputStream(), fileDestPath, session.key());
        } catch (IOException ex) {
            throw new StorageException("Error storing file", ex);
        }
        //update the file size
        String sql = "UPDATE files SET size = ? WHERE id = ?";
        dataService.update(sql, multipartFile.getSize(), idFile);
    }

    /**
     * Retrieves a file from the storage
     *
     * @param filePath   user's file path
     * @param outputPath path to store the decrypted file
     * @param session    user's session
     * @return file data like id and size
     * @throws ResourceNotFoundException if the file does not exist
     */
    public FileData1 retrieve(String filePath, Path outputPath, SessionDTO session) {
        var data = resolveFile(filePath, session.rootDir());
        if (data.fileId == 0) throw new ResourceNotFoundException("File " + filePath + " does not exist");
        Path inputFile = computeFilePath(data.fileId);
        try (var inputStream = Files.newInputStream(inputFile)) {
            CryptHelper.decrypt(inputStream, outputPath, session.key());
            return data;
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
    private int resolveDir(String path, int rootDir) {
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
    private FileData1 resolveFile(String path, int rootDir) {
        if ("/".equals(path)) throw new ResourceNotFoundException("Invalid file path");
        if (!path.startsWith("/")) throw new ResourceNotFoundException("Path must start with /");
        int slashIndex = path.lastIndexOf("/");
        String dirPath = path.substring(0, slashIndex + 1);
        String fileName = path.substring(slashIndex + 1);
        int dirId = resolveDir(dirPath, rootDir);
        String sql = "SELECT id, size FROM files WHERE name = ? AND directory_id = ?";
        var fileData = dataService.queryOne(sql, rs -> new FileData2(rs.getInt(1), rs.getInt(2)), fileName, dirId);
        return fileData.map(o -> new FileData1(dirId, o.idFile(), fileName, o.size())).orElseGet(() -> new FileData1(dirId, 0, fileName, 0));
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

    public record FileData1(int dirId, int fileId, String fileName, int size) {
    }

    public record FileData2(int idFile, int size) {
    }

}
