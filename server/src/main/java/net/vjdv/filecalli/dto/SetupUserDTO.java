package net.vjdv.filecalli.dto;

/**
 * users in setup.yml
 */
public record SetupUserDTO(String id, String name, String pass, String role, boolean webdav, SetupWebdavTokenDTO[] webdavTokens) {
}
