package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.RetrievedFileDTO;
import net.vjdv.filecalli.dto.WebdavSessionDTO;
import net.vjdv.filecalli.dto.webdav.Multistatus;
import net.vjdv.filecalli.exceptions.ResourceNotFoundException;
import net.vjdv.filecalli.exceptions.StorageException;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class WebdavService {

    private final DataService dataService;
    private final StorageService storageService;

    public WebdavService(DataService dataService, StorageService storageService) {
        this.dataService = dataService;
        this.storageService = storageService;
    }

    public Multistatus propfind(String path, int rootDir) {
        return path.endsWith("/") ? propfindDir(path, rootDir) : propfindFile(path, rootDir);
    }

    private Multistatus propfindDir(String path, int rootDir) {
        var builder = Multistatus.builder();
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        var dirData = storageService.resolveDir(path, rootDir, true);
        // directories
        String sql1 = "SELECT name, created_at, last_modified FROM directories WHERE parent = ?";
        dataService.forEach(sql1, rs -> {
            String name = rs.getString(1);
            long createdAt = rs.getLong(2);
            long lastModified = rs.getLong(3);
            builder.directory(name, dirData.path() + "/" + name, createdAt, lastModified);
        }, dirData.id());
        // files
        String sql2 = "SELECT name, size, mime, created_at, last_modified FROM files WHERE directory_id = ?";
        dataService.forEach(sql2, rs -> {
            String name = rs.getString("name");
            String mime = rs.getString("mime");
            long size = rs.getLong("size");
            long createdAt = rs.getLong("created_at");
            long lastModified = rs.getLong("last_modified");
            builder.file(name, dirData.path() + "/" + name, mime, size, createdAt, lastModified);
        }, dirData.id());
        //return
        return builder.build();
    }

    private Multistatus propfindFile(String path, int rootDir) {
        var data = storageService.resolveFile(path, rootDir);
        if (data.id() == 0) throw new ResourceNotFoundException("File " + path + " does not exist");
        return Multistatus.builder().file(data.name(), data.path(), data.mime(), data.size(), data.createdAt(), data.lastModified()).build();
    }

    public RetrievedFileDTO retrieve(String path, WebdavSessionDTO wdsessionDTO) {
        return storageService.retrieve(path, wdsessionDTO.toSessionDTO());
    }

    public void store(String path, String mime, long size, InputStream input, WebdavSessionDTO wdsessionDTO) {
        storageService.store(path, mime, size, input, wdsessionDTO.toSessionDTO());
    }

    public void delete(String path, WebdavSessionDTO wdsessionDTO) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
            storageService.deleteDirectory(path, true, wdsessionDTO.rootDir());
        } else {
            storageService.delete(path, wdsessionDTO.rootDir());
        }
    }

    public void makeCollection(String path, WebdavSessionDTO wdsession) {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        storageService.createDirectory(path, wdsession.toSessionDTO());
    }

    public void move(String from, String to, WebdavSessionDTO wdsession) {
        if (from.endsWith("/") && !to.endsWith("/")) throw new StorageException("cannot move directory to file");
        if (!from.endsWith("/") && to.endsWith("/")) throw new StorageException("cannot move file to directory");
        try {
            storageService.moveDirectory(from, to, wdsession.rootDir());
        } catch (ResourceNotFoundException ex) {
            storageService.moveFile(from, to, wdsession.rootDir());
        }
    }

}
