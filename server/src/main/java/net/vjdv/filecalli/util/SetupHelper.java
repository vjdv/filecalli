package net.vjdv.filecalli.util;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.SetupUserDTO;
import net.vjdv.filecalli.exceptions.DataException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

@Slf4j
public class SetupHelper {

    public static void createTables(Connection conn) {
        log.info("Creating tables");
        createTable(conn, "directories", """
                CREATE TABLE directories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    parent INTEGER NULL,
                    owner TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_modified INTEGER NOT NULL,
                    FOREIGN KEY (parent) REFERENCES directories (id)
                )""");
        createTable(conn, "users", """
                CREATE TABLE users (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    password BLOB NOT NULL,
                    root_directory INTEGER NOT NULL,
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
        var sql1 = "INSERT INTO users (id, name, password, root_directory) VALUES (?, ?, ?, 99999)";
        var sql2 = "INSERT INTO directories (name, owner, created_at, last_modified) VALUES ('/', ?, ?, ?)";
        var sql3 = "UPDATE users SET root_directory = ? WHERE id = ?";
        for (SetupUserDTO user : users) {
            // insert user
            try (var ps = conn.prepareStatement(sql1)) {
                ps.setString(1, user.id());
                ps.setString(2, user.name());
                ps.setBytes(3, CryptHelper.hashBytes(user.pass()));
                ps.execute();
            } catch (SQLException ex) {
                throw new DataException("Error creating user", ex);
            }
            // insert root directory of user
            int directoryId;
            try (var ps = conn.prepareStatement(sql2)) {
                long now = Instant.now().toEpochMilli();
                ps.setString(1, user.id());
                ps.setLong(2, now);
                ps.setLong(3, now);
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
        }
    }

}
