package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.SessionDTO;
import net.vjdv.filecalli.exceptions.LoginException;
import net.vjdv.filecalli.util.CryptHelper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SessionService {

    private final DataService dataService;
    private final Map<String, SessionDTO> sessions = new HashMap<>();

    public SessionService(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Login a user and create a session
     *
     * @param userId the user id
     * @param pass   the user password
     * @return the session uid
     * @throws LoginException if the user or password are invalid
     */
    public String login(String userId, String pass) {
        String sql = "SELECT id, name FROM users WHERE id = ? AND password = ?";
        String uid = java.util.UUID.randomUUID().toString();
        dataService.query(sql, rs -> {
            if (!rs.next()) {
                throw new LoginException("Invalid user or password");
            }
            String name = rs.getString("name");
            byte[] token = CryptHelper.hashBytes(pass + userId);
            SessionDTO session = new SessionDTO(userId, name, System.currentTimeMillis() + 3600000, token);
            sessions.put(uid, session);
        }, userId, CryptHelper.hashBytes(pass));
        return uid;
    }

    /**
     * Get a session by it assigned uid
     *
     * @param uid the session uid
     * @return session data
     */
    public SessionDTO getSession(String uid) {
        if (uid == null) throw new LoginException("No uid was provided");
        if (!sessions.containsKey(uid)) throw new LoginException("Session not found");
        var session = sessions.get(uid);
        if (session.expiration() < System.currentTimeMillis()) {
            sessions.remove(uid);
            throw new LoginException("Session expired");
        }
        return session;
    }

}
