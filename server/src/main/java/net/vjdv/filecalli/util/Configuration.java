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
    private final Path dataPath;
    private final String salt;

    public Configuration(Environment env) {
        //path where all encrypted files are stored
        dataPath = Paths.get(env.getProperty("datapath", "./data"));
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
