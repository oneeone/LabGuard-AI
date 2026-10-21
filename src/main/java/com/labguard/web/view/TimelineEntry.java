package com.labguard.web.view;

import com.labguard.model.AuditEvent;
import com.labguard.model.WorkOrderStatus;
import com.labguard.web.WebFormat;

/**
 * 工单处理时间线的一行。动作、状态变化与操作人在 ViewModel 里转成文案，模板只负责展示。
 */
public record TimelineEntry(
        String actionLabel,
        String statusText,
        String operatorText,
        String commentText,
        String timeText
) {

    public static TimelineEntry of(AuditEvent event, String remediationNote) {
        return new TimelineEntry(
                actionLabel(event.action()),
                statusText(event.fromStatus(), event.toStatus()),
                text(event.operator()),
                commentText(event, remediationNote),
                WebFormat.dateTime(event.createdAt()));
    }

    private static String actionLabel(String action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case "CREATE_WORK_ORDER" -> "创建整改工单";
            case "SUBMIT_REMEDIATION" -> "提交整改";
            case "PASS_REVIEW" -> "复查通过";
            case "RETURN_REMEDIATION" -> "退回整改";
            default -> action;
        };
    }

    /** 创建事件没有前置状态，只显示目标状态。 */
    private static String statusText(String from, String to) {
        String toLabel = statusLabel(to);
        if (toLabel.isEmpty()) {
            return "";
        }
        String fromLabel = statusLabel(from);
        return fromLabel.isEmpty() ? toLabel : fromLabel + " → " + toLabel;
    }

    private static String statusLabel(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        try {
            return WorkOrderStatus.valueOf(name).label();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    /** 提交整改优先展示当次整改说明；与动作名重复的备注不再重复展示。 */
    private static String commentText(AuditEvent event, String remediationNote) {
        if (remediationNote != null && !remediationNote.isBlank()) {
            return remediationNote;
        }
        String comment = event.comment() == null ? "" : event.comment().trim();
        return comment.isEmpty() || comment.equals(actionLabel(event.action())) ? null : comment;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
