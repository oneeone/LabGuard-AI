package com.labguard.service;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AiResultSource;
import com.labguard.model.HazardItem;
import com.labguard.model.RemediationContext;
import com.labguard.model.RemediationReview;
import com.labguard.model.ReviewOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DemoAiVisionServiceTest {

    @Test
    @DisplayName("A-01 演示模式返回 DEMO 来源和固定结构化风险")
    void returnsDemoSourceAndFixedHazard() {
        AiAnalysisResult result = new DemoAiVisionService().analyze(
                ImageInput.of("fake".getBytes(StandardCharsets.UTF_8), "lab.png"));

        assertThat(result.source()).isEqualTo(AiResultSource.DEMO);
        assertThat(result.sourceLabel()).isEqualTo("演示数据：未调用真实视觉 API");
        assertThat(result.model()).isEqualTo("demo-sample");
        assertThat(result.rawResponse()).isNull();
        assertThat(result.analyzedAt()).isNotNull();
        assertThat(result.hazards()).hasSize(1);

        HazardItem hazard = result.hazards().get(0);
        assertThat(hazard.demoData()).isTrue();
        assertThat(hazard.needsHumanReview()).isTrue();
        assertThat(hazard.category()).contains("演示");
        assertThat(hazard.evidence()).contains("未调用真实视觉 API");
        assertThat(hazard.uncertainty()).contains("演示样例");
    }

    @Test
    @DisplayName("A-19-10 演示复查固定 INCONCLUSIVE + DEMO，不伪装成通过")
    void demoReviewIsInconclusiveDemo() {
        DemoAiVisionService service = new DemoAiVisionService();
        RemediationReview review = service.reviewRemediation(
                ImageInput.of("fake".getBytes(StandardCharsets.UTF_8), "after.png"),
                new RemediationContext("WO-1", "BLOCKED_EXIT", "移除杂物并保持通道畅通"));

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.INCONCLUSIVE);
        assertThat(review.source()).isEqualTo(AiResultSource.DEMO);
        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.evidence()).contains("演示数据");
        assertThat(review.suggestedAction()).contains("现场核验");
    }
}
