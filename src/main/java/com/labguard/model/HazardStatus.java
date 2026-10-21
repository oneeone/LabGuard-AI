package com.labguard.model;

/**
 * 隐患的人工确认状态。AI 结果落库后为 PENDING_CONFIRM，
 * 只有 CONFIRMED 的隐患才能创建整改工单。
 */
public enum HazardStatus {

    PENDING_CONFIRM("待确认"),
    CONFIRMED("已确认"),
    REJECTED("已驳回");

    private final String label;

    HazardStatus(String label) {
        this.label = label;
    }

    /** 页面展示用中文文案。 */
    public String label() {
        return label;
    }
}
