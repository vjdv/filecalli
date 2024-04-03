package net.vjdv.filecalli.dto;

import javax.crypto.SecretKey;

public record SessionDTO(String userId, String userName, long expiration, SecretKey key) {
}
