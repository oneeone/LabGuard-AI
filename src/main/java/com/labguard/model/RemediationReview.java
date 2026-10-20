package com.labguard.model;

import java.time.Instant;
import java.util.List;

/**
 * 整改照片复查结果。
 * <p>
 * 边界约束（构造时强制）：
 * - needsHumanReview 恒为 true：任何复查建议都需要人工确认，AI 不直接关闭工单；
 * - PASSED 只表示模型建议；若 PASSED 但证据为空，会被降级为 INCONCLUSIVE（失败不当作通过）；
 * - 来源必须区分 REAL / DEMO，演示结果不得当作真实复查结论。
 */
public record RemediationReview(
        ReviewOutcome outcome,
        String evidence,
        List<Hazard> remainingHazards,
        String uncertainty,
        boolean needsHumanReview,
        String suggestedAction,
        AiResultSource source,
        String model,
        Instant analyzedAt,
        String rawResponse
) {
    public RemediationReview {
        if (outcome == null) {
            throw new IllegalArgumentException("复查结果缺少 outcome");
        }
        if (source == null) {
            throw new IllegalArgumentException("复查结果缺少来源标记");
        }
        evidence = evidence == null ? "" : evidence.trim();
        uncertainty = uncertainty == null ? "" : uncertainty.trim();
        suggestedAction = suggestedAction == null ? "" : suggestedAction.trim();
        remainingHazards = remainingHazards == null ? List.of() : List.copyOf(remainingHazards);
        if (outcome == ReviewOutcome.PASSED && evidence.isBlank()) {
            // 失败（证据不足）不能被当作通过：降级为无法判断。
            outcome = ReviewOutcome.INCONCLUSIVE;
            if (suggestedAction.isBlank()) {
                suggestedAction = "复查证据不足，请重新拍摄或现场核验。";
            }
        }
        // 复查结果一律需要人工确认。
        needsHumanReview = true;
    }
}
