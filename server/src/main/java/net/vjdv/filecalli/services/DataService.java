package net.vjdv.filecalli.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.ResultSetWrapper;
import net.vjdv.filecalli.dto.SetupDTO;
import net.vjdv.filecalli.exceptions.DataException;
import net.vjdv.filecalli.util.Configuration;
import net.vjdv.filecalli.util.SetupHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;

@Slf4j
@Service
public class DataService {

    private final Connection connection;

    public DataService(Configuration configuration) {
        Path dataPath = configuration.getDataPath();
        Path setupPath = dataPath.resolve("setup.yml");
        Path dbPath = dataPath.resolve("db.sqlite");
        log.info("sqlite path is {}", dbPath);
        if (!Files.exists(dbPath) && !Files.exists(setupPath)) {
            throw new DataException("Please run setup first.");
        }
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
        } catch (ClassNotFoundException | SQLException ex) {
            throw new DataException("Error opening database", ex);
        }
        if (Files.exists(setupPath)) {
            log.info("Will run setup");
            SetupHelper.createTables(connection);
            log.info("Parsing setup.yml");
            ObjectMapper mapper = new YAMLMapper();
            SetupDTO setupDto;
            try {
                setupDto = mapper.readValue(setupPath.toFile(), SetupDTO.class);
            } catch (IOException ex) {
                throw new DataException("Error reading setup.yml", ex);
            }
            SetupHelper.setupUsers(connection, setupDto.users());
            try {
                Files.delete(setupPath);
            } catch (IOException ex) {
                throw new DataException("Error deleting setup.yml", ex);
            }
        }
    }

    /**
     * Execute a query and process the result set
     *
     * @param sql      the query
     * @param consumer the consumer to process the result set
     * @param params   the query parameters
     */
    public void query(String sql, Consumer<ResultSetWrapper> consumer, Object... params) {
        try (var stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String) {
                    stmt.setString(i + 1, (String) params[i]);
                } else if (params[i] instanceof Integer) {
                    stmt.setInt(i + 1, (int) params[i]);
                } else if (params[i] instanceof byte[]) {
                    stmt.setBytes(i + 1, (byte[]) params[i]);
                } else {
                    stmt.setObject(i + 1, params[i]);
                }
            }
            try (var rs = stmt.executeQuery()) {
                consumer.accept(new ResultSetWrapper(rs));
            }
        } catch (SQLException ex) {
            throw new DataException("Error executing query", ex);
        }
    }

    @PreDestroy
    public void close() {
        log.info("Closing database");
        try {
            connection.close();
        } catch (SQLException ex) {
            log.error("Error closing db file", ex);
        }
    }


}
