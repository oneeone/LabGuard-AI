package com.labguard.model;

/**
 * 工单状态。工单在人工确认后创建，创建即进入待整改，不存在"待确认"状态。
 * 合法转换规则见 {@link com.labguard.workflow.WorkOrderStateMachine}。
 */
public enum WorkOrderStatus {

    PENDING_REMEDIATION("待整改"),
    PENDING_REVIEW("待复查"),
    CLOSED("已关闭");

    private final String label;

    WorkOrderStatus(String label) {
        this.label = label;
    }

    /** 页面展示用中文文案。 */
    public String label() {
        return label;
    }
}
