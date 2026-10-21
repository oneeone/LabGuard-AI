package com.labguard.service;

import com.labguard.model.HazardItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 风险结果规范化 + 人工核验规则（对已结构化的结果做规范化，不解析原始报文）。
 * <p>
 * 规则（只增不减，绝不推翻模型的不确定结论）：
 * - 模型明确 needsHumanReview=true 必须保留；
 * - 置信度低于阈值（VISION_MIN_CONFIDENCE，默认 0.6）触发人工核验；
 * - 证据为空或过短触发人工核验，并标记 evidenceSufficient=false；
 * - 模型给出不确定性说明时追加核验原因，不改写为确定结论；
 * - 配置了类别白名单时，未知类别保留原文并追加“未知风险类别”，绝不静默映射为已知类别。
 */
@Component
public class HazardReviewRules {

    /** 证据短于该长度视为不足以支撑判断。 */
    static final int MIN_EVIDENCE_LENGTH = 8;

    private final AiVisionProperties properties;

    public HazardReviewRules(AiVisionProperties properties) {
        this.properties = properties;
    }

    public HazardItem apply(HazardItem hazard) {
        List<String> reasons = new ArrayList<>(hazard.reviewReasons());
        boolean needsHumanReview = hazard.needsHumanReview();
        boolean evidenceSufficient = isNotBlank(hazard.evidence())
                && hazard.evidence().trim().length() >= MIN_EVIDENCE_LENGTH;

        if (!evidenceSufficient) {
            needsHumanReview = true;
            reasons.add("证据不足：可见证据为空或过短，无法支持风险判断");
        }
        if (hazard.confidence() != null && hazard.confidence() < properties.resolvedMinConfidence()) {
            needsHumanReview = true;
            reasons.add("低置信度：模型置信度 " + hazard.confidence()
                    + " 低于阈值 " + properties.resolvedMinConfidence());
        }
        if (isNotBlank(hazard.uncertainty())) {
            needsHumanReview = true;
            reasons.add("模型给出不确定性说明，需人工确认");
        }
        String category = normalizeCategory(hazard.category());
        if (!knownCategory(category)) {
            needsHumanReview = true;
            reasons.add("未知风险类别，未被白名单识别：" + category);
        }

        return new HazardItem(category, hazard.riskLevel(), hazard.location(), hazard.evidence(),
                hazard.confidence(), hazard.uncertainty(), hazard.suggestedAction(),
                needsHumanReview, hazard.demoData(), reasons, evidenceSufficient);
    }

    /** 只做大写/去空白规范化；未知类别原样保留，不映射为任何已知类别。 */
    private String normalizeCategory(String category) {
        return category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
    }

    /** 白名单为空时不限制类别；配置后按大写精确匹配。 */
    private boolean knownCategory(String category) {
        List<String> whitelist = properties.resolvedCategoryWhitelist();
        if (whitelist.isEmpty()) {
            return true;
        }
        return whitelist.stream()
                .map(item -> item == null ? "" : item.trim().toUpperCase(Locale.ROOT))
                .anyMatch(item -> item.equals(category));
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
