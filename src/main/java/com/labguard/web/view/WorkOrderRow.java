package com.labguard.web.view;

import com.labguard.model.Hazard;
import com.labguard.model.RiskLevel;
import com.labguard.model.WorkOrder;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;
import com.labguard.web.WebFormat;

import java.time.LocalDate;

/**
 * S2 工单列表行。把状态标签、超期判断和日期文案算好，模板不做业务判断。
 */
public record WorkOrderRow(
        long id,
        long hazardId,
        String category,
        String riskLevelLabel,
        String riskLevelClass,
        String labName,
        String assignee,
        String dueDateText,
        String statusLabel,
        String statusClass,
        boolean overdue,
        boolean pendingRemediation,
        boolean pendingReview,
        boolean closed
) {

    public static WorkOrderRow of(WorkOrderDetail detail) {
        WorkOrder order = detail.order();
        Hazard hazard = detail.hazard();
        boolean closed = order.status() == WorkOrderStatus.CLOSED;
        boolean overdue = order.dueDate() != null
                && order.dueDate().isBefore(LocalDate.now())
                && !closed;
        return new WorkOrderRow(
                order.id(),
                order.hazardId(),
                hazard.category(),
                hazard.riskLevel() == null ? RiskLevel.UNKNOWN.label() : hazard.riskLevel().label(),
                riskLevelCss(hazard.riskLevel()),
                detail.labName(),
                order.assignee(),
                WebFormat.date(order.dueDate()),
                order.status() == null ? "" : order.status().label(),
                statusCss(order.status()),
                overdue,
                order.status() == WorkOrderStatus.PENDING_REMEDIATION,
                order.status() == WorkOrderStatus.PENDING_REVIEW,
                closed);
    }

    public static String statusCss(WorkOrderStatus status) {
        if (status == null) {
            return "pending";
        }
        return switch (status) {
            case PENDING_REMEDIATION -> "remediate";
            case PENDING_REVIEW -> "review";
            case CLOSED -> "closed";
        };
    }

    private static String riskLevelCss(RiskLevel riskLevel) {
        if (riskLevel == null) {
            return "unknown";
        }
        return switch (riskLevel) {
            case HIGH -> "high";
            case MEDIUM -> "medium";
            case LOW -> "low";
            case UNKNOWN -> "unknown";
        };
    }
}
