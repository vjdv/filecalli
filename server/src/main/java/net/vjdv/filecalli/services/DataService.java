package net.vjdv.filecalli.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
