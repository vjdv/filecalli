package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.SessionDTO;
import net.vjdv.filecalli.dto.WebdavSessionDTO;
import net.vjdv.filecalli.exceptions.LoginException;
import net.vjdv.filecalli.exceptions.ServiceException;
import net.vjdv.filecalli.util.CryptHelper;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SessionService {

    private final DataService dataService;
    private final Map<String, SessionDTO> sessions = new HashMap<>();
    private final Map<String, WebdavSessionDTO> wdsessions = new HashMap<>();

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
        String sql = "SELECT id, name, root_directory, webdav_suffix FROM users WHERE id = ? AND password = ?";
        String uid = java.util.UUID.randomUUID().toString();
        dataService.query(sql, rs -> {
            if (!rs.next()) {
                throw new LoginException("Invalid user or password");
            }
            String name = rs.getString("name");
            int rootDir = rs.getInt("root_directory");
            String webdavSuffix = rs.getString("webdav_suffix");
            //files key
            byte[] keyBytes = CryptHelper.hashBytes(pass + userId);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            //webdav key
            SecretKey webdavKey = null;
            if (webdavSuffix != null) {
                byte[] webdavKeyBytes = CryptHelper.hashBytes(userId + webdavSuffix);
                webdavKey = new SecretKeySpec(webdavKeyBytes, "AES");
            }
            //session object
            SessionDTO session = new SessionDTO(userId, name, rootDir, System.currentTimeMillis() + 3600000, key, webdavKey);
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

    /**
     * Get a session from a basic auth string
     *
     * @param b64 the basic auth string (withoiut the "Basic" prefix)
     * @return session data
     * @throws LoginException if the basic auth is invalid
     */
    public WebdavSessionDTO getSessionFromBasicAuth(String b64) {
        if (b64 == null) throw new LoginException("No basic auth was provided");
        if (wdsessions.containsKey(b64)) return wdsessions.get(b64);
        //reads base64 and extract values
        String[] parts;
        try {
            String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
            parts = decoded.split(":");
            if (parts.length != 2) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new LoginException("Invalid basic auth");
        }
        //query from db
        String sql = "SELECT w.path, u.webdav_suffix, u.root_directory FROM webdav_tokens W INNER JOIN users U ON w.user_id = u.id WHERE w.token = ? AND u.id = ?";
        var session = dataService.queryOne(sql, rs -> {
            String path = "/webdav" + rs.getString(1);
            String suffix = rs.getString(2);
            int rootDir = rs.getInt(3);
            if (suffix == null) {
                throw new ServiceException("Webdav not enabled for user");
            }
            byte[] keyBytes = CryptHelper.hashBytes(parts[0] + suffix);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            return new WebdavSessionDTO(parts[0], path, rootDir, key);
        }, parts[1], parts[0]).orElseThrow(() -> new LoginException("invalid user or password"));
        //cache and return
        wdsessions.put(b64, session);
        return session;
    }

}
