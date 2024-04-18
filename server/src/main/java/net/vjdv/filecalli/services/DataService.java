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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

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
     * Execute an insert statement and return the autoincremented id
     *
     * @param sql    the insert statement
     * @param params the parameters
     * @return the autoincremented id
     */
    public int insertAutoincrement(String sql, Object... params) {
        try (var stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            fillParameters(stmt, params);
            stmt.execute();
            return stmt.getGeneratedKeys().getInt(1);
        } catch (SQLException ex) {
            throw new DataException("Error executing insert", ex);
        }
    }

    /**
     * Execute an update statement
     *
     * @param sql    the update statement
     * @param params the parameters
     * @return the number of rows affected
     */
    public int update(String sql, Object... params) {
        try (var stmt = connection.prepareStatement(sql)) {
            fillParameters(stmt, params);
            return stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new DataException("Error executing update", ex);
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
            //fill parameters
            fillParameters(stmt, params);
            //execute query
            try (var rs = stmt.executeQuery()) {
                consumer.accept(new ResultSetWrapper(rs));
            }
        } catch (SQLException ex) {
            throw new DataException("Error executing query", ex);
        }
    }

    /**
     * Execute a query, process the result set and return one value
     *
     * @param sql      sql query
     * @param function function to process the result set
     * @param params   query parameters
     * @param <T>      the return type of the function
     * @return the optional value returned by the function, empty if the result set is empty
     */
    public <T> Optional<T> queryOne(String sql, Function<ResultSetWrapper, T> function, Object... params) {
        AtomicReference<T> result = new AtomicReference<>(null);
        query(sql, rs -> {
            if (rs.next()) {
                T item = function.apply(rs);
                result.set(item);
            }
        }, params);
        return Optional.ofNullable(result.get());
    }

    /**
     * Execute a query, process the result set and return a list of values
     *
     * @param sql      sql query
     * @param function function to process the result set
     * @param params   query parameters
     * @param <T>      the return type of the function
     * @return values available in the result set
     */
    public <T> List<T> queryList(String sql, Function<ResultSetWrapper, T> function, Object... params) {
        List<T> list = new ArrayList<>();
        query(sql, rs -> {
            while (rs.next()) {
                T item = function.apply(rs);
                list.add(item);
            }
        }, params);
        return list;
    }

    /**
     * Fill the parameters of a prepared statement
     *
     * @param stmt   prepared statement
     * @param params parameters
     * @throws SQLException if there is an error setting the parameters
     */
    private void fillParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof String param) {
                stmt.setString(i + 1, param);
            } else if (params[i] instanceof Integer param) {
                stmt.setInt(i + 1, param);
            } else if (params[i] instanceof Long param) {
                stmt.setLong(i + 1, param);
            } else if (params[i] instanceof byte[] param) {
                stmt.setBytes(i + 1, param);
            } else {
                stmt.setObject(i + 1, params[i]);
            }
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
