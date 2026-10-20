package com.labguard.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 视觉 AI 配置。变量名与任务卡一致：VISION_MODE / VISION_API_BASE_URL / VISION_API_KEY /
 * VISION_MODEL / VISION_CONNECT_TIMEOUT / VISION_READ_TIMEOUT，
 * 另含人工核验规则参数 VISION_MIN_CONFIDENCE 与类别白名单 VISION_CATEGORY_WHITELIST。
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
    private double minConfidence = 0.6;
    private List<String> categoryWhitelist = List.of();

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

    /** 置信度低于该阈值时必须触发人工核验。 */
    public double resolvedMinConfidence() {
        return minConfidence >= 0 && minConfidence <= 1 ? minConfidence : 0.6;
    }

    /** 类别白名单（大写比较）；为空表示不限制类别，但未知类别绝不静默映射为已知类别。 */
    public List<String> resolvedCategoryWhitelist() {
        return categoryWhitelist == null ? List.of() : categoryWhitelist;
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

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public List<String> getCategoryWhitelist() {
        return categoryWhitelist;
    }

    public void setCategoryWhitelist(List<String> categoryWhitelist) {
        this.categoryWhitelist = categoryWhitelist;
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
