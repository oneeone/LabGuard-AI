package com.labguard.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 视觉 AI 配置。变量名与任务卡一致：VISION_MODE / VISION_API_BASE_URL / VISION_API_KEY /
 * VISION_MODEL / VISION_CONNECT_TIMEOUT / VISION_READ_TIMEOUT。
 */
@Component
@ConfigurationProperties(prefix = "labguard.ai")
public class AiVisionProperties {
    private String mode = "demo";
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);

    public boolean realMode() {
        return "real".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public boolean complete() {
        return missingConfiguration().isEmpty();
    }

    public List<String> missingConfiguration() {
        List<String> missing = new ArrayList<>();
        if (isBlank(baseUrl)) {
            missing.add("VISION_API_BASE_URL");
        }
        if (isBlank(apiKey)) {
            missing.add("VISION_API_KEY");
        }
        if (isBlank(model)) {
            missing.add("VISION_MODEL");
        }
        return missing;
    }

    public Duration resolvedConnectTimeout() {
        return connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
    }

    public Duration resolvedReadTimeout() {
        return readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
