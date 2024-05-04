package net.vjdv.filecalli.util;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.SetupUserDTO;
import net.vjdv.filecalli.enums.Parameter;
import net.vjdv.filecalli.exceptions.DataException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
public class SetupHelper {

    public static void createTables(Connection conn) {
        log.info("Creating tables");
        createTable(conn, "directories", """
                CREATE TABLE directories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    parent INTEGER NULL,
                    created_at INTEGER NOT NULL,
                    last_modified INTEGER NOT NULL,
                    FOREIGN KEY (parent) REFERENCES directories (id)
                )""");
        createTable(conn, "users", """
                CREATE TABLE users (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    password BLOB NOT NULL,
                    role TEXT NOT NULL,
                    root_directory INTEGER NOT NULL,
                    webdav_suffix TEXT NULL,
                    FOREIGN KEY (root_directory) REFERENCES directories (id)
                )""");
        createTable(conn, "files", """
                CREATE TABLE files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    mime TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_modified INTEGER NOT NULL,
                    directory_id INTEGER NOT NULL,
                    FOREIGN KEY (directory_id) REFERENCES directories (id)
                )""");
        createTable(conn, "webdav_tokens", """
                CREATE TABLE webdav_tokens (
                    token TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    path TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users (id)
                )""");
        createTable(conn, "parameters", """
                CREATE TABLE parameters (
                    ikey INTEGER PRIMARY KEY,
                    value TEXT NOT NULL
                )""");
        //parameters
        String sql = "INSERT INTO parameters (ikey, value) VALUES (?, ?)";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Parameter.DATA_VERSION.getValue());
            ps.setString(2, "1");
            ps.execute();
        } catch (SQLException ex) {
            throw new DataException("Error creating parameters", ex);
        }
    }

    private static void createTable(Connection conn, String name, String sql) {
        try (var ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException ex) {
            throw new DataException("Error creating table " + name, ex);
        }
    }

    public static void setupUsers(Connection conn, SetupUserDTO[] users) {
        log.info("Creating users");
        var sql1 = "INSERT INTO users (id, name, password, role, root_directory) VALUES (?, ?, ?, ?, 99999)";
        var sql2 = "INSERT INTO directories (name, created_at, last_modified) VALUES ('/', ?, ?)";
        var sql3 = "UPDATE users SET root_directory = ? WHERE id = ?";
        for (SetupUserDTO user : users) {
            // insert user
            try (var ps = conn.prepareStatement(sql1)) {
                ps.setString(1, user.id());
                ps.setString(2, user.name());
                ps.setBytes(3, CryptHelper.hashBytes(user.pass()));
                ps.setString(4, user.role());
                ps.execute();
            } catch (SQLException ex) {
                throw new DataException("Error creating user", ex);
            }
            // insert root directory of user
            int directoryId;
            try (var ps = conn.prepareStatement(sql2)) {
                long now = Instant.now().toEpochMilli();
                ps.setLong(1, now);
                ps.setLong(2, now);
                ps.execute();
                directoryId = ps.getGeneratedKeys().getInt(1);
            } catch (SQLException ex) {
                throw new DataException("Error creating directory", ex);
            }
            // update user with root directory
            try (var ps = conn.prepareStatement(sql3)) {
                ps.setInt(1, directoryId);
                ps.setString(2, user.id());
                ps.execute();
            } catch (SQLException ex) {
                throw new DataException("Error updating user", ex);
            }
            //enable webdav
            if (user.webdav()) {
                enableWebdav(conn, user.id(), directoryId);
                //setup tokens
                for (var token : user.webdavTokens()) {
                    var sql4 = "INSERT INTO webdav_tokens (token, user_id, path, created_at) VALUES (?, ?, ?, ?)";
                    try (var ps = conn.prepareStatement(sql4)) {
                        ps.setString(1, token.token());
                        ps.setString(2, user.id());
                        ps.setString(3, token.path());
                        ps.setLong(4, Instant.now().toEpochMilli());
                        ps.execute();
                    } catch (SQLException ex) {
                        throw new DataException("Error creating webdav token", ex);
                    }
                }
            }
        }
    }

    /**
     * Creates webdav suffix enabling webdav for user
     */
    public static void enableWebdav(Connection conn, String userId, int rootDirectory) {
        log.info("Enabling webdav for user {}", userId);
        //creates suffix
        var sql = "UPDATE users SET webdav_suffix = ? WHERE id = ?";
        try (var ps = conn.prepareStatement(sql)) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            ps.setString(1, suffix);
            ps.setString(2, userId);
            ps.execute();
        } catch (SQLException ex) {
            throw new DataException("Error enabling webdav", ex);
        }
        // insert webdav directory of user
        var sql2 = "INSERT INTO directories (name, parent, created_at, last_modified) VALUES ('webdav', ?, ?, ?)";
        try (var ps = conn.prepareStatement(sql2)) {
            long now = Instant.now().toEpochMilli();
            ps.setInt(1, rootDirectory);
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.execute();
        } catch (SQLException ex) {
            throw new DataException("Error creating directory", ex);
        }
    }

}
