package com.labguard.model;

/**
 * 整改照片复查的上下文：告诉模型要核验的原风险、原始证据和整改目标。
 * AI 服务只读取该上下文做分析，不修改任何工单状态。
 */
public record RemediationContext(
        String orderNo,
        String originalCategory,
        String originalDescription,
        String remediationGoal,
        String originalSuggestedAction,
        String remediationNote,
        String originalEvidence,
        String originalLocation
) {
    public RemediationContext {
        if (isBlank(originalCategory)) {
            throw new IllegalArgumentException("复查上下文缺少原风险类别");
        }
        if (isBlank(remediationGoal)) {
            throw new IllegalArgumentException("复查上下文缺少整改目标");
        }
        if (isBlank(originalEvidence) && isBlank(originalLocation)) {
            throw new IllegalArgumentException("复查上下文缺少原始证据与位置，无法确定核验点");
        }
        orderNo = trimOrEmpty(orderNo);
        originalDescription = trimOrEmpty(originalDescription);
        originalSuggestedAction = trimOrEmpty(originalSuggestedAction);
        remediationNote = trimOrEmpty(remediationNote);
        originalEvidence = trimOrEmpty(originalEvidence);
        originalLocation = trimOrEmpty(originalLocation);
    }

    /** 兼容旧调用方：不含原始证据。 */
    public RemediationContext(String orderNo, String originalCategory, String originalDescription,
                              String remediationGoal, String originalSuggestedAction,
                              String remediationNote) {
        this(orderNo, originalCategory, originalDescription, remediationGoal,
                originalSuggestedAction, remediationNote, "", originalDescription);
    }

    /** 只带必填字段的便捷构造。 */
    public RemediationContext(String orderNo, String originalCategory, String remediationGoal) {
        this(orderNo, originalCategory, "", remediationGoal, "", "", "", originalCategory);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
