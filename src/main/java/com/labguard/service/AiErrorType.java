package com.labguard.service;

/**
 * AI 服务错误分类。用户可见提示统一由 {@link #defaultMessage()} 提供。
 */
public enum AiErrorType {
    NOT_CONFIGURED("视觉 API 未配置完整，请配置 VISION_API_BASE_URL、VISION_API_KEY 和 VISION_MODEL。"),
    UNSUPPORTED_IMAGE("仅支持 JPG、JPEG 或 PNG 图片，且单张大小不超过 10MB。"),
    TIMEOUT("视觉 API 请求超时，请检查网络或稍后重试。"),
    AUTH_FAILED("视觉 API 鉴权失败，请检查 API 密钥配置。"),
    RATE_LIMITED("视觉 API 调用过于频繁，请稍后重试。"),
    SERVER_ERROR("视觉 API 服务暂时不可用，请稍后重试。"),
    NETWORK_ERROR("无法连接视觉 API，请检查网络或 API 地址。"),
    REQUEST_FAILED("视觉 API 请求失败，请稍后重试。"),
    INVALID_RESPONSE("视觉 API 返回格式无法识别，请保留原始错误并人工核验。");

    private final String defaultMessage;

    AiErrorType(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
