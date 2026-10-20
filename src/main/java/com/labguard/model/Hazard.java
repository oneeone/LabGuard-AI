package com.labguard.model;

import java.time.LocalDateTime;

/**
 * 隐患。由 AI 分析结果落库后进入 PENDING_CONFIRM 状态，
 * 经人工确认（CONFIRMED）后才允许创建整改工单。
 */
public record Hazard(
        long id,
        long inspectionId,
        String category,
        RiskLevel riskLevel,
        RiskLevelSource riskLevelSource,
        String location,
        String evidence,
        Double confidence,
        String uncertainty,
        String suggestedAction,
        boolean needsHumanReview,
        HazardStatus status,
        String reviewNote,
        String confirmedBy,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt
) {

    /** 由 AI 结果新建隐患，风险等级来源默认为规则建议，初始状态待确认。 */
    public static Hazard fromAi(long inspectionId, String category, RiskLevel riskLevel,
                                String location, String evidence, Double confidence,
                                String uncertainty, String suggestedAction, boolean needsHumanReview) {
        return new Hazard(0L, inspectionId, category, riskLevel, RiskLevelSource.RULE,
                location, evidence, confidence, uncertainty, suggestedAction, needsHumanReview,
                HazardStatus.PENDING_CONFIRM, null, null, null, LocalDateTime.now());
    }
}
