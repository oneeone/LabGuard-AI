package com.labguard.model;

/**
 * 逐证据点核验结果：point 是原始证据原文，status 是该证据点本次的判定，
 * visibleEvidence 是本次照片中支撑判定的可见依据，reason 是补充说明。
 */
public record EvidenceCheck(
        String point,
        CheckStatus status,
        String visibleEvidence,
        String reason
) {
    public EvidenceCheck {
        point = point == null ? "" : point.trim();
        status = status == null ? CheckStatus.INCONCLUSIVE : status;
        visibleEvidence = visibleEvidence == null ? "" : visibleEvidence.trim();
        reason = reason == null ? "" : reason.trim();
    }
}
