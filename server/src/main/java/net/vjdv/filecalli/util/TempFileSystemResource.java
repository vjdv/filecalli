package net.vjdv.filecalli.util;

import org.springframework.core.io.FileSystemResource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TempFileSystemResource extends FileSystemResource {

    private final Path file;

    public TempFileSystemResource(Path path) {
        super(path);
        this.file = path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file.toFile()) {
            @Override
            public void close() throws IOException {
                super.close();
                Files.delete(file);
            }
        };
    }

}
