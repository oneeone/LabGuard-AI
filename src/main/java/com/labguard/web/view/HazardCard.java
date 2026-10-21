package com.labguard.web.view;

import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * S1 分析结果页的单条风险卡片。把"缺失时显示什么"、置信度档位、按钮显隐
 * 统一算好，模板只负责输出，避免在 HTML 里堆判断。
 */
public record HazardCard(
        long id,
        long inspectionId,
        String category,
        String riskLevelLabel,
        String riskLevelClass,
        String riskLevelSourceLabel,
        String location,
        String evidence,
        boolean confidenceProvided,
        int confidencePercent,
        String confidenceLabel,
        String confidenceClass,
        boolean needsHumanReview,
        List<String> suggestedActions,
        String statusLabel,
        String statusClass,
        boolean pending,
        boolean confirmed,
        boolean rejected,
        String reviewNote,
        boolean canCreateWorkOrder,
        String categoryInput,
        String locationInput,
        String evidenceInput,
        String suggestedActionInput,
        String riskLevelName
) {

    private static final Pattern ACTION_SEPARATORS = Pattern.compile("[\\n；;]+");
    private static final String FALLBACK_ACTION = "暂无建议";

    public static HazardCard of(Hazard hazard, boolean hasWorkOrder) {
        RiskLevel riskLevel = hazard.riskLevel();
        Double confidence = hazard.confidence();
        boolean provided = confidence != null;

        String confidenceLabel;
        String confidenceClass;
        if (!provided) {
            confidenceLabel = "未提供";
            confidenceClass = "unknown";
        } else if (confidence >= 0.80d) {
            confidenceLabel = "高";
            confidenceClass = "high";
        } else if (confidence >= 0.50d) {
            confidenceLabel = "中";
            confidenceClass = "medium";
        } else {
            confidenceLabel = "低";
            confidenceClass = "low";
        }

        boolean lowConfidence = provided && confidence < 0.50d;
        boolean evidenceMissing = isBlank(hazard.evidence());
        boolean needsHumanReview = hazard.needsHumanReview() || lowConfidence || evidenceMissing;

        HazardStatus status = hazard.status();
        return new HazardCard(
                hazard.id(),
                hazard.inspectionId(),
                isBlank(hazard.category()) ? "未分类" : hazard.category(),
                riskLevel == null ? RiskLevel.UNKNOWN.label() : riskLevel.label(),
                riskLevelCss(riskLevel),
                hazard.riskLevelSource() == null ? "" : hazard.riskLevelSource().label(),
                isBlank(hazard.location()) ? "位置未说明" : hazard.location(),
                evidenceMissing ? "无可见证据" : hazard.evidence(),
                provided,
                provided ? (int) Math.round(Math.max(0d, Math.min(1d, confidence)) * 100) : 0,
                confidenceLabel,
                confidenceClass,
                needsHumanReview,
                splitActions(hazard.suggestedAction()),
                status == null ? "" : status.label(),
                statusCss(status),
                status == HazardStatus.PENDING_CONFIRM,
                status == HazardStatus.CONFIRMED,
                status == HazardStatus.REJECTED,
                hazard.reviewNote(),
                status == HazardStatus.CONFIRMED && !hasWorkOrder,
                nullToEmpty(hazard.category()),
                nullToEmpty(hazard.location()),
                nullToEmpty(hazard.evidence()),
                nullToEmpty(hazard.suggestedAction()),
                riskLevel == null ? "" : riskLevel.name());
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

    private static String statusCss(HazardStatus status) {
        if (status == null) {
            return "pending";
        }
        return switch (status) {
            case PENDING_CONFIRM -> "pending";
            case CONFIRMED -> "confirmed";
            case REJECTED -> "rejected";
        };
    }

    private static List<String> splitActions(String suggestedAction) {
        if (isBlank(suggestedAction)) {
            return List.of(FALLBACK_ACTION);
        }
        List<String> actions = new ArrayList<>();
        for (String part : ACTION_SEPARATORS.split(suggestedAction)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                actions.add(trimmed);
            }
        }
        return actions.isEmpty() ? List.of(FALLBACK_ACTION) : List.copyOf(actions);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
