package net.vjdv.filecalli.dto;

public record SessionDTO(String userId, String userName,  long expiration , byte[] token) {
}
