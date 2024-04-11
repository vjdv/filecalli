package net.vjdv.filecalli.dto;

import javax.crypto.SecretKey;

public record SessionDTO(String userId, String userName, int rootDir, long expiration, SecretKey key) {
}
