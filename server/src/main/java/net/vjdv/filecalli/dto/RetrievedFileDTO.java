package net.vjdv.filecalli.dto;

import java.nio.file.Path;

public record RetrievedFileDTO(
        String name,
        String mimeType,
        long size,
        long createdAt,
        long lastModified,
        Path path
) {

    public RetrievedFileDTO(FileDataDTO data, Path path) {
        this(data.name(), data.mime(), data.size(), data.createdAt(), data.lastModified(), path);
    }

}
