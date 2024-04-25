package net.vjdv.filecalli.dto;

public record DirDataDTO(
        int id,
        String name,
        String path,
        long createdAt,
        long lastModified,
        int parentId
) {

}
