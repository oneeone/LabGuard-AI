package com.labguard.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在 Spring 容器启动前加载项目根目录下的 .env 文件到 Environment。
 *
 * <p>属性优先级：操作系统环境变量 &gt; .env &gt; application.properties。
 * 默认读取 ./.env，可通过系统属性 {@code labguard.env.file} 或
 * 环境变量 {@code LABGUARD_ENV_FILE} 指定其他路径。</p>
 */
public class EnvConfig implements EnvironmentPostProcessor {

    private static final String ENV_FILE_PROPERTY = "labguard.env.file";
    private static final String ENV_FILE_ENV_VAR = "LABGUARD_ENV_FILE";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String customPath = System.getProperty(ENV_FILE_PROPERTY, System.getenv(ENV_FILE_ENV_VAR));
        Path envFile = Path.of(customPath != null ? customPath : ".env");

        if (!Files.isRegularFile(envFile)) {
            return;
        }

        Map<String, Object> properties = parse(envFile);
        if (properties.isEmpty()) {
            return;
        }

        PropertySource<?> source = new MapPropertySource("labguardDotenv", properties);
        // 放在 OS 环境变量之后，使 OS 环境变量可覆盖 .env，.env 可覆盖 application.properties
        if (environment.getPropertySources().contains("systemEnvironment")) {
            environment.getPropertySources().addAfter("systemEnvironment", source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
    }

    private Map<String, Object> parse(Path envFile) {
        Map<String, Object> properties = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                // 去掉首尾成对的双引号
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    properties.put(key, value);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法读取环境配置文件: " + envFile.toAbsolutePath(), e);
        }
        return properties;
    }
}
