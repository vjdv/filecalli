package net.vjdv.filecalli.util;

import lombok.Getter;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Getter
@Component
public class Configuration {
    private static Configuration instance = null;
    private final String host;
    private final String contextPath;
    private final Path dataPath;
    private final Path tempPath;
    private final String salt;

    public Configuration(Environment env) {
        //host where the server is running
        host = env.getProperty("host", "http://localhost:8080");
        //context path or base path
        contextPath = env.getProperty("contextpath", "");
        //path where all encrypted files are stored
        dataPath = Paths.get(env.getProperty("datapath", "./data"));
        //path where temporary files are stored
        tempPath = Paths.get(env.getProperty("temppath", "./temp"));
        //salt used for hashes and encryption
        salt = env.getProperty("salt", "calli");
        instance = this;
    }

    /**
     * Makes available the instance to helpers
     *
     * @return The instance of the configuration
     */
    public static Configuration getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Configuration not initialized");
        }
        return instance;
    }

}
