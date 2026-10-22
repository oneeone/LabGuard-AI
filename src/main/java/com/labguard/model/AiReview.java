package com.labguard.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 已落库的 AI 核验记录。与 {@link RemediationReview} 的区别是带上工单号与主键，
 * 供详情页常驻展示；核验结论始终只是建议，不改变工单状态。
 */
public record AiReview(
        long id,
        long workOrderId,
        ReviewOutcome outcome,
        String evidence,
        List<EvidenceCheck> checks,
        Comparability comparability,
        String uncertainty,
        String suggestedAction,
        AiResultSource source,
        String model,
        String rawResponse,
        boolean needsHumanReview,
        LocalDateTime createdAt
) {
    public AiReview {
        outcome = outcome == null ? ReviewOutcome.INCONCLUSIVE : outcome;
        evidence = evidence == null ? "" : evidence;
        checks = checks == null ? List.of() : List.copyOf(checks);
        comparability = comparability == null ? Comparability.COMPARABLE : comparability;
        uncertainty = uncertainty == null ? "" : uncertainty;
        suggestedAction = suggestedAction == null ? "" : suggestedAction;
        source = source == null ? AiResultSource.DEMO : source;
        model = model == null ? "" : model;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        // AI 核验结果一律需要人工确认。
        needsHumanReview = true;
    }

    public static AiReview from(long workOrderId, RemediationReview review) {
        return new AiReview(0, workOrderId, review.outcome(), review.evidence(), review.checks(),
                review.comparability(), review.uncertainty(), review.suggestedAction(), review.source(),
                review.model(), review.rawResponse(), true, LocalDateTime.now());
    }

    public AiReview withId(long newId) {
        return new AiReview(newId, workOrderId, outcome, evidence, checks, comparability, uncertainty,
                suggestedAction, source, model, rawResponse, needsHumanReview, createdAt);
    }

    /** 页面展示用时间文本，避免模板依赖额外的时间方言。 */
    public String createdAtText() {
        return createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
