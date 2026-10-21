package com.labguard.workflow;

/**
 * 业务规则异常。由全局异常处理器统一捕获后渲染为页面提示，
 * 页面直接使用 {@link #getMessage()} 即可，不需要再做映射。
 */
public class WorkflowException extends RuntimeException {

    public enum Code {
        HAZARD_NOT_FOUND,
        HAZARD_NOT_CONFIRMED,
        HAZARD_ALREADY_HAS_ORDER,
        WORK_ORDER_NOT_FOUND,
        ILLEGAL_TRANSITION,
        INVALID_ARGUMENT
    }

    private final Code code;

    public WorkflowException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public WorkflowException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
