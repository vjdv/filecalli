package net.vjdv.filecalli;

import net.vjdv.filecalli.util.Configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FileCalliApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileCalliApplication.class, args);
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer(Configuration conf) {
        return factory -> factory.setContextPath(conf.getContextPath());
    }

}
