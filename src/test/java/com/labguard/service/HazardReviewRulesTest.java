package com.labguard.service;

import com.labguard.model.Hazard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HazardReviewRulesTest {

    private final AiVisionProperties properties = new AiVisionProperties();
    private final HazardReviewRules rules = new HazardReviewRules(properties);

    @Test
    @DisplayName("A-19-06 证据为空强制人工核验（规则层直接验证）")
    void emptyEvidenceForcesReview() {
        Hazard review = rules.apply(hazard("", 0.95, "", false));

        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.evidenceSufficient()).isFalse();
        assertThat(review.reviewReasons()).anyMatch(reason -> reason.contains("证据不足"));
    }

    @Test
    @DisplayName("A-19-07 模型标记 true 时保留，规范化不会清除")
    void modelFlagNeverCleared() {
        Hazard review = rules.apply(hazard("通道口可见堆放的纸箱阻挡通行", 0.99, "", true));

        assertThat(review.needsHumanReview()).isTrue();
    }

    @Test
    @DisplayName("证据充分、高置信度、无不确定性时不追加核验")
    void cleanHazardPassesThrough() {
        Hazard review = rules.apply(hazard("通道口可见堆放的纸箱阻挡通行", 0.99, "", false));

        assertThat(review.needsHumanReview()).isFalse();
        assertThat(review.evidenceSufficient()).isTrue();
        assertThat(review.reviewReasons()).isEmpty();
    }

    @Test
    @DisplayName("置信度缺失（null）不误判为低置信度")
    void nullConfidenceNotFlagged() {
        Hazard base = new Hazard("BLOCKED_EXIT", "中", "左侧", "通道口可见堆放的纸箱阻挡通行",
                null, "", "移除杂物", false, false);
        Hazard review = rules.apply(base);

        assertThat(review.needsHumanReview()).isFalse();
        assertThat(review.reviewReasons()).isEmpty();
    }

    @Test
    @DisplayName("阈值可通过配置调整")
    void thresholdConfigurable() {
        properties.setMinConfidence(0.9);
        Hazard review = rules.apply(hazard("通道口可见堆放的纸箱阻挡通行", 0.85, "", false));

        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.reviewReasons()).anyMatch(reason -> reason.contains("低置信度"));
    }

    @Test
    @DisplayName("白名单外的原始类别不会被映射成白名单内的已知类别")
    void unknownCategoryKeepsOriginalText() {
        properties.setCategoryWhitelist(List.of("BLOCKED_EXIT", "CHEMICAL_STORAGE"));
        Hazard review = rules.apply(hazard("不明化学桶堆放", "通道口可见两个无标签化学桶", 0.99, "", false));

        assertThat(review.category()).isEqualTo("不明化学桶堆放");
        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.reviewReasons()).anyMatch(reason -> reason.contains("未知风险类别"));
    }

    private Hazard hazard(String evidence, double confidence, String uncertainty, boolean needsHumanReview) {
        return new Hazard("BLOCKED_EXIT", "中", "左侧通道", evidence, confidence, uncertainty,
                "移除杂物并保持通道畅通", needsHumanReview, false);
    }

    private Hazard hazard(String category, String evidence, double confidence, String uncertainty,
                          boolean needsHumanReview) {
        return new Hazard(category, "中", "左侧通道", evidence, confidence, uncertainty,
                "移除杂物并保持通道畅通", needsHumanReview, false);
    }
}
