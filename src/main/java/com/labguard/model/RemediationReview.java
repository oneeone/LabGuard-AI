package com.labguard.model;

import java.time.Instant;
import java.util.List;

/**
 * 整改照片复查结果。
 * <p>
 * 结论由逐证据点推导，模型不能直接决定整体结论：
 * - 任一点「仍存在」→ FAILED；
 * - 全部「已解决」且整体证据非空 → PASSED；
 * - 其余情况（含无法判断、无可比性）→ INCONCLUSIVE。
 * <p>
 * 边界约束（构造时强制）：
 * - needsHumanReview 恒为 true：任何复查建议都需要人工确认，AI 不直接关闭工单；
 * - PASSED 只表示模型建议；若 PASSED 但证据为空，会被降级为 INCONCLUSIVE；
 * - 来源必须区分 REAL / DEMO，演示结果不得当作真实复查结论。
 */
public record RemediationReview(
        ReviewOutcome outcome,
        String evidence,
        List<HazardItem> remainingHazards,
        String uncertainty,
        boolean needsHumanReview,
        String suggestedAction,
        AiResultSource source,
        String model,
        Instant analyzedAt,
        String rawResponse,
        List<EvidenceCheck> checks,
        Comparability comparability
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
        checks = checks == null ? List.of() : List.copyOf(checks);
        comparability = comparability == null ? Comparability.COMPARABLE : comparability;

        // 逐点推导整体结论，规则只做收紧、不做放宽。
        if (comparability == Comparability.NOT_COMPARABLE) {
            outcome = ReviewOutcome.INCONCLUSIVE;
        } else if (!checks.isEmpty()) {
            boolean anyUnresolved = checks.stream().anyMatch(c -> c.status() == CheckStatus.UNRESOLVED);
            boolean allResolved = checks.stream().allMatch(c -> c.status() == CheckStatus.RESOLVED);
            if (anyUnresolved) {
                outcome = ReviewOutcome.FAILED;
            } else if (allResolved && !evidence.isBlank()) {
                outcome = ReviewOutcome.PASSED;
            } else {
                outcome = ReviewOutcome.INCONCLUSIVE;
            }
        } else if (outcome == ReviewOutcome.PASSED) {
            // 没有逐点核验结果时不允许给出通过建议。
            outcome = ReviewOutcome.INCONCLUSIVE;
        }

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

    /** 兼容旧调用方：不含逐点核验与可比性判定。 */
    public RemediationReview(ReviewOutcome outcome, String evidence, List<HazardItem> remainingHazards,
                             String uncertainty, boolean needsHumanReview, String suggestedAction,
                             AiResultSource source, String model, Instant analyzedAt, String rawResponse) {
        this(outcome, evidence, remainingHazards, uncertainty, needsHumanReview, suggestedAction,
                source, model, analyzedAt, rawResponse, List.of(), Comparability.COMPARABLE);
    }
}
