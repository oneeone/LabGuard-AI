package com.labguard.service;

/**
 * AI 服务的结构化异常。消息面向用户展示，detail 用于日志和排查，不包含密钥或图片内容。
 */
public class AiVisionException extends RuntimeException {
    private final AiErrorType type;
    private final String detail;

    public AiVisionException(AiErrorType type) {
        this(type, null, null);
    }

    public AiVisionException(AiErrorType type, String detail) {
        this(type, detail, null);
    }

    public AiVisionException(AiErrorType type, String detail, Throwable cause) {
        super(type.defaultMessage(), cause);
        this.type = type;
        this.detail = detail;
    }

    public AiErrorType type() {
        return type;
    }

    public String detail() {
        return detail;
    }
}
