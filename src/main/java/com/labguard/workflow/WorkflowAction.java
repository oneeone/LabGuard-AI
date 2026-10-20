package com.labguard.workflow;

/**
 * 审计事件动作名。写入 audit_event.action 字段，用于还原处理时间线。
 */
public enum WorkflowAction {

    CREATE_INSPECTION,
    ANALYZE,
    CONFIRM_HAZARD,
    EDIT_HAZARD,
    REJECT_HAZARD,
    CREATE_WORK_ORDER,
    SUBMIT_REMEDIATION,
    PASS_REVIEW,
    RETURN_REMEDIATION
}
