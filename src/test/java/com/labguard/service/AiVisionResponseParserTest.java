package com.labguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AiResultSource;
import com.labguard.model.Hazard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiVisionResponseParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiVisionResponseParser parser = new AiVisionResponseParser(mapper);

    @Test
    @DisplayName("A-11 合法响应解析为 REAL 来源和完整字段")
    void parsesValidResponse() throws Exception {
        AiAnalysisResult result = parser.parse(envelope(contract(validHazard())), "vision-test-model");

        assertThat(result.source()).isEqualTo(AiResultSource.REAL);
        assertThat(result.hazards()).hasSize(1);
        Hazard hazard = result.hazards().get(0);
        assertThat(hazard.category()).isEqualTo("BLOCKED_EXIT");
        assertThat(hazard.riskLevel()).isEqualTo("高");
        assertThat(hazard.location()).isEqualTo("画面左侧安全出口前");
        assertThat(hazard.evidence()).contains("纸箱");
        assertThat(hazard.confidence()).isEqualTo(0.86);
        assertThat(hazard.uncertainty()).contains("现场核验");
        assertThat(hazard.suggestedAction()).contains("移除杂物");
        assertThat(hazard.needsHumanReview()).isTrue();
        assertThat(hazard.demoData()).isFalse();
        assertThat(result.model()).isEqualTo("vision-test-model");
        assertThat(result.rawResponse()).contains("BLOCKED_EXIT");
        assertThat(result.analyzedAt()).isNotNull();
    }

    @Test
    @DisplayName("A-07 非法 JSON 返回 INVALID_RESPONSE")
    void rejectsInvalidJson() throws Exception {
        assertInvalid(envelope("这不是 JSON"), "非法 JSON");
    }

    @Test
    @DisplayName("A-08 缺少必填字段返回 INVALID_RESPONSE")
    void rejectsMissingFields() throws Exception {
        for (String field : new String[]{"category", "location", "evidence", "suggestedAction"}) {
            ObjectNode hazard = validHazard();
            hazard.remove(field);
            assertInvalid(envelope(contract(hazard)), field);
        }
    }

    @Test
    @DisplayName("A-09 confidence 超出 0 到 1 返回 INVALID_RESPONSE")
    void rejectsConfidenceOutOfRange() throws Exception {
        ObjectNode tooHigh = validHazard();
        tooHigh.put("confidence", 1.5);
        assertInvalid(envelope(contract(tooHigh)), "confidence");

        ObjectNode tooLow = validHazard();
        tooLow.put("confidence", -0.1);
        assertInvalid(envelope(contract(tooLow)), "confidence");
    }

    @Test
    @DisplayName("needsHumanReview 必须是布尔值")
    void rejectsNonBooleanHumanReview() throws Exception {
        ObjectNode hazard = validHazard();
        hazard.put("needsHumanReview", "yes");
        assertInvalid(envelope(contract(hazard)), "needsHumanReview");
    }

    @Test
    @DisplayName("顶层缺少 hazards 数组返回 INVALID_RESPONSE")
    void rejectsMissingHazardsArray() {
        assertInvalid("{\"result\":\"ok\"}", "hazards");
    }

    @Test
    @DisplayName("hazards 为空数组是合法结果")
    void acceptsEmptyHazards() throws Exception {
        AiAnalysisResult result = parser.parse(contract(), "vision-test-model");

        assertThat(result.hazards()).isEmpty();
        assertThat(result.source()).isEqualTo(AiResultSource.REAL);
    }

    @Test
    @DisplayName("兼容 Markdown 代码块包裹的 JSON")
    void acceptsCodeFence() throws Exception {
        String content = "```json\n" + contract(validHazard()) + "\n```";

        AiAnalysisResult result = parser.parse(envelope(content), "vision-test-model");

        assertThat(result.hazards()).hasSize(1);
    }

    @Test
    @DisplayName("riskLevel 缺失时标记待评估，非法取值被拒绝")
    void validatesRiskLevel() throws Exception {
        ObjectNode missing = validHazard();
        missing.remove("riskLevel");
        assertThat(parser.parse(contract(missing), "m").hazards().get(0).riskLevel()).isEqualTo("待评估");

        ObjectNode invalid = validHazard();
        invalid.put("riskLevel", "严重");
        assertInvalid(envelope(contract(invalid)), "riskLevel");
    }

    private void assertInvalid(String body, String expectedDetail) {
        assertThatThrownBy(() -> parser.parse(body, "m"))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE);
                    assertThat(ex.detail()).contains(expectedDetail);
                });
    }

    private ObjectNode validHazard() {
        ObjectNode hazard = mapper.createObjectNode();
        hazard.put("category", "BLOCKED_EXIT");
        hazard.put("riskLevel", "高");
        hazard.put("location", "画面左侧安全出口前");
        hazard.put("evidence", "可见纸箱和杂物占用出口前通道");
        hazard.put("confidence", 0.86);
        hazard.put("uncertainty", "仅依据当前视角判断，需现场核验");
        hazard.put("suggestedAction", "移除杂物并保持安全出口通道畅通");
        hazard.put("needsHumanReview", true);
        return hazard;
    }

    private String contract(ObjectNode... hazards) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode array = root.putArray("hazards");
        for (ObjectNode hazard : hazards) {
            array.add(hazard);
        }
        return mapper.writeValueAsString(root);
    }

    private String envelope(String content) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return mapper.writeValueAsString(root);
    }
}
