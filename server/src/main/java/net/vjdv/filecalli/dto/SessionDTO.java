package net.vjdv.filecalli.dto;

import net.vjdv.filecalli.enums.Role;

import javax.crypto.SecretKey;

public record SessionDTO(String userId, String userName, Role role, int rootDir, long expiration, SecretKey key, SecretKey webdavKey) {
}
