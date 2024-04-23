package net.vjdv.filecalli.services;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.util.Configuration;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Helper for scheduled tasks
 */
@Slf4j
@Service
public class TasksService {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Configuration config;

    public TasksService(Configuration configuration) {
        this.config = configuration;
    }

    /**
     * Creates a temporary file and schedules its deletion
     */
    public Path getTempFile() throws IOException {
        var file = Files.createTempFile(config.getTempPath(), "fc", ".tmp");
        executor.schedule(() -> {
            try {
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    Files.delete(file);
                }
            } catch (IOException ex) {
                log.warn("Error al borrar archivo temporal", ex);
            }
        }, 10, TimeUnit.MINUTES);
        return file;
    }

}
