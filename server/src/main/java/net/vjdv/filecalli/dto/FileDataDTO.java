package net.vjdv.filecalli.dto;

public record FileDataDTO(
        int id,
        String name,
        String mime,
        long size,
        long createdAt,
        long lastModified,
        int directoryId
) {

}
