package com.labguard.model;

import java.util.List;

/**
 * 单条视觉风险结果。
 * <p>
 * 字段语义：
 * - riskLevel 是“风险等级建议”（suggestedRiskLevel），来自模型识别线索，不等于最终安全结论；
 * - needsHumanReview 为 true 时结果必须保留人工核验，任何规范化步骤都不得清除；
 * - reviewReasons 记录触发人工核验的原因（低置信度 / 证据不足 / 不确定性等）；
 * - evidenceSufficient 表示可见证据是否足以支持该风险判断。
 */
public record Hazard(
        String category,
        String riskLevel,
        String location,
        String evidence,
        Double confidence,
        String uncertainty,
        String suggestedAction,
        boolean needsHumanReview,
        boolean demoData,
        List<String> reviewReasons,
        boolean evidenceSufficient
) {
    public Hazard {
        reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
    }

    /** 兼容旧调用方（Controller / 工单 / 演示样例）：核验原因由规则器补全。 */
    public Hazard(String category, String riskLevel, String location, String evidence,
                  Double confidence, String uncertainty, String suggestedAction,
                  boolean needsHumanReview, boolean demoData) {
        this(category, riskLevel, location, evidence, confidence, uncertainty, suggestedAction,
                needsHumanReview, demoData, List.of(), true);
    }
}
