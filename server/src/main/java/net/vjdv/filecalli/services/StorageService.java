package net.vjdv.filecalli.services;

import net.vjdv.filecalli.exceptions.StorageException;
import net.vjdv.filecalli.util.Configuration;
import net.vjdv.filecalli.util.CryptHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class StorageService {

    private final DataService dataService;
    private final Configuration config;

    public StorageService(DataService dataService, Configuration configuration) {
        this.dataService = dataService;
        this.config = configuration;
    }

    public void store(int idPath, int idFile, MultipartFile multipartFile, SecretKey key) {
        FileInfo info;
        if (idFile == 0) info = allocateRow(idPath, multipartFile.getOriginalFilename());
        else info = recoverRow(idFile);
        System.out.println("info: " + info);
        Path parent = config.getDataPath().resolve(info.path);
        //parent directory must exist
        if (!Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (Exception ex) {
                throw new StorageException("Error creating directories", ex);
            }
        }
        //store the file
        Path file = parent.resolve(Integer.toHexString(info.id));
        try {
            CryptHelper.encrypt(multipartFile.getInputStream(), file, key);
        } catch (IOException ex) {
            throw new StorageException("Error storing file", ex);
        }
        //update the file size
        String sql = "UPDATE files SET size = ? WHERE id = ?";
        dataService.update(sql, multipartFile.getSize(), info.id);
    }

    public Path retrieve(int idFile, SecretKey key) {
        FileInfo info = recoverRow(idFile);
        Path inputFile = config.getDataPath().resolve(info.path).resolve(Integer.toHexString(info.id));
        Path outputFile = config.getTempPath().resolve(Integer.toHexString(info.id));
        try (var inputStream = Files.newInputStream(inputFile)) {
            CryptHelper.decrypt(inputStream, outputFile, key);
            return outputFile;
        } catch (IOException ex) {
            throw new StorageException("Error retrieving file", ex);
        }
    }

    private FileInfo allocateRow(int dirId, String fileName) {
        String sql = "INSERT INTO files (name, path, size, directory_id) VALUES (?, ?, 0, ?)";
        int fileId = dataService.insertAutoincrement(sql, fileName, "xx", dirId);
        String path = Integer.toHexString(fileId / 1000 + 160);
        sql = "UPDATE files SET path = ? WHERE id = ?";
        dataService.update(sql, path, fileId);
        return new FileInfo(fileId, path);
    }

    private FileInfo recoverRow(int idFile) {
        String sql = "SELECT id, path FROM files WHERE id = ?";
        return dataService.query(sql, rs -> new FileInfo(idFile, rs.getString(2)), idFile);
    }


    public record FileInfo(int id, String path) {
    }

}
