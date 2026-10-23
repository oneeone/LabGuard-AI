package com.labguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LabGuardApplication {

    public static void main(String[] args) {
        ensureDefaultDataDirectory();
        SpringApplication.run(LabGuardApplication.class, args);
    }

    /** 默认 SQLite 路径为 ./data/labguard.db；SQLite 不会自动创建父目录。 */
    private static void ensureDefaultDataDirectory() {
        try {
            Files.createDirectories(Path.of("data"));
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建数据目录 data", ex);
        }
    }
}
