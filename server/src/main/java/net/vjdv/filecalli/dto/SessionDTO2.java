package net.vjdv.filecalli.dto;

public record SessionDTO2(String userId, String userName) {
    public SessionDTO2(SessionDTO o) {
        this(o.userId(), o.userName());
    }
}
