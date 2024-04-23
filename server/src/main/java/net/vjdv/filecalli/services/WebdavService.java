package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.webdav.Multistatus;
import net.vjdv.filecalli.exceptions.ResourceNotFoundException;
import net.vjdv.filecalli.util.Configuration;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebdavService {

    private final DataService dataService;
    private final StorageService storageService;
    private final Configuration config;

    public WebdavService(DataService dataService, StorageService storageService, Configuration configuration) {
        this.dataService = dataService;
        this.storageService = storageService;
        this.config = configuration;
    }

    public Multistatus propfind(String path, int rootDir) {
        return path.endsWith("/") ? propfindDir(path, rootDir) : propfindFile(path, rootDir);
    }

    private Multistatus propfindDir(String path, int rootDir) {
        var builder = Multistatus.builder();
        int directoryId = storageService.resolveDir(path, rootDir);
        // directories
        String sql1 = "SELECT name, created_at, last_modified FROM directories WHERE parent = ?";
        dataService.forEach(sql1, rs -> {
            String name = rs.getString(1);
            long createdAt = rs.getLong(2);
            long lastModified = rs.getLong(3);
            builder.directory(name, createdAt, lastModified);
        }, directoryId);
        // files
        String sql2 = "SELECT name, size, mime, created_at, last_modified FROM files WHERE directory_id = ?";
        dataService.forEach(sql2, rs -> {
            String name = rs.getString("name");
            String mime = rs.getString("mime");
            long size = rs.getLong("size");
            long createdAt = rs.getLong("created_at");
            long lastModified = rs.getLong("last_modified");
            builder.file(name, mime, size, createdAt, lastModified);
        }, directoryId);
        //return
        return builder.build();
    }

    private Multistatus propfindFile(String path, int rootDir) {
        var data = storageService.resolveFile(path, rootDir);
        if (data.fileId() == 0) throw new ResourceNotFoundException("File " + path + " does not exist");
        String sql = "SELECT name, mime, size, created_at, last_modified FROM files WHERE id = ?";
        return dataService.queryOne(sql, rs -> {
            String name = rs.getString("name");
            String mime = rs.getString("mime");
            long size = rs.getLong("size");
            long createdAt = rs.getLong("created_at");
            long lastModified = rs.getLong("last_modified");
            return Multistatus.builder().file(name, mime, size, createdAt, lastModified).build();
        }, data.fileId()).orElseThrow(() -> new ResourceNotFoundException("File " + path + " does not exist"));
    }

}
