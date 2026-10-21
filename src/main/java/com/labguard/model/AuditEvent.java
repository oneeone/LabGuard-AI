package com.labguard.model;

import java.time.LocalDateTime;

/**
 * 操作审计事件。每次状态变化写入一条，与业务更新同事务提交。
 * detail 用于保存结构化快照（如整改说明与整改照片路径），支持退回后的历史追溯。
 */
public record AuditEvent(
        long id,
        String entityType,
        long entityId,
        String action,
        String fromStatus,
        String toStatus,
        String operator,
        String comment,
        String detail,
        LocalDateTime createdAt
) {

    public static final String ENTITY_INSPECTION = "INSPECTION";
    public static final String ENTITY_HAZARD = "HAZARD";
    public static final String ENTITY_WORK_ORDER = "WORK_ORDER";

    public static AuditEvent of(String entityType, long entityId, String action,
                                String fromStatus, String toStatus, String operator,
                                String comment, String detail) {
        return new AuditEvent(0L, entityType, entityId, action, fromStatus, toStatus,
                operator, comment, detail, LocalDateTime.now());
    }
}
