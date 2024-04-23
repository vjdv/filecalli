package net.vjdv.filecalli.dto;

import javax.crypto.SecretKey;

public record WebdavSessionDTO(String userId, String path, int rootDir, SecretKey key) {

    public SessionDTO toSessionDTO() {
        return new SessionDTO(userId, "Webdav User", rootDir, 0, null, key);
    }

}
