package com.labguard.service;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AiResultSource;
import com.labguard.model.Hazard;
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

        Hazard hazard = result.hazards().get(0);
        assertThat(hazard.demoData()).isTrue();
        assertThat(hazard.needsHumanReview()).isTrue();
        assertThat(hazard.category()).contains("演示");
        assertThat(hazard.evidence()).contains("未调用真实视觉 API");
        assertThat(hazard.uncertainty()).contains("演示样例");
    }
}
