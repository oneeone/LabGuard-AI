package com.labguard.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 整改工单。由已确认的隐患创建，创建即进入 PENDING_REMEDIATION。
 * 整改说明与整改照片只保留最新一次，历史由 AuditEvent 承载。
 */
public record WorkOrder(
        long id,
        long hazardId,
        String assignee,
        LocalDate dueDate,
        WorkOrderStatus status,
        String note,
        String remediationNote,
        String afterImagePath,
        String reviewComment,
        String reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 新建工单，状态固定为待整改。 */
    public static WorkOrder create(long hazardId, String assignee, LocalDate dueDate, String note) {
        LocalDateTime now = LocalDateTime.now();
        return new WorkOrder(0L, hazardId, assignee, dueDate, WorkOrderStatus.PENDING_REMEDIATION,
                note, null, null, null, null, null, now, now);
    }
}
