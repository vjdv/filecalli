package net.vjdv.filecalli.dto;

import net.vjdv.filecalli.enums.Role;

import javax.crypto.SecretKey;

public record WebdavSessionDTO(String userId, String path, Role role, int rootDir, SecretKey key) {

    public SessionDTO toSessionDTO() {
        return new SessionDTO(userId, "Webdav User", role, rootDir, 0, null, key);
    }

}
