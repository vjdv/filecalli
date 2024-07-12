package net.vjdv.filecalli.dto;

public record ListedResource(String name,
                             boolean isDirectory,
                             boolean isRegularFile,
                             String path,
                             int size,
                             long createdAt,
                             long lastModified) {
}
