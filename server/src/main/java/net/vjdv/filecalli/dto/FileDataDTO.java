package net.vjdv.filecalli.dto;

public record FileDataDTO(
        int id,
        String name,
        String path,
        String mime,
        long size,
        long createdAt,
        long lastModified,
        int directoryId
) {

    public boolean exists() {
        return id != 0;
    }

}
