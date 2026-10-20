package com.labguard.model;

/**
 * 整改照片复查的上下文：告诉模型需要验证的原风险和整改目标。
 * AI 服务只读取该上下文做分析，不修改任何工单状态。
 */
public record RemediationContext(
        String orderNo,
        String originalCategory,
        String originalDescription,
        String remediationGoal,
        String originalSuggestedAction,
        String remediationNote
) {
    public RemediationContext {
        if (isBlank(originalCategory)) {
            throw new IllegalArgumentException("复查上下文缺少原风险类别");
        }
        if (isBlank(remediationGoal)) {
            throw new IllegalArgumentException("复查上下文缺少整改目标");
        }
        orderNo = trimOrEmpty(orderNo);
        originalDescription = trimOrEmpty(originalDescription);
        originalSuggestedAction = trimOrEmpty(originalSuggestedAction);
        remediationNote = trimOrEmpty(remediationNote);
    }

    /** 只带必填字段的便捷构造。 */
    public RemediationContext(String orderNo, String originalCategory, String remediationGoal) {
        this(orderNo, originalCategory, "", remediationGoal, "", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
