package com.labguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AiResultSource;
import com.labguard.model.HazardItem;
import com.labguard.model.RemediationReview;
import com.labguard.model.ReviewOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 视觉模型响应校验器：模型输出必须先通过这里，才能进入业务层。
 * 结构校验：hazards 必须是数组；每条风险必须有 category/location/evidence/suggestedAction；
 * confidence 存在时必须是 0~1 的数字；needsHumanReview 存在时必须是布尔值。
 * 通过结构校验后，再交给 {@link HazardReviewRules} 做人工核验规则与规范化。
 */
@Component
public class AiVisionResponseParser {
    private static final Logger log = LoggerFactory.getLogger(AiVisionResponseParser.class);

    private static final Map<String, String> RISK_LEVELS = Map.ofEntries(
            Map.entry("高", "高"), Map.entry("中", "中"), Map.entry("低", "低"),
            Map.entry("高风险", "高"), Map.entry("中风险", "中"), Map.entry("低风险", "低"),
            Map.entry("high", "高"), Map.entry("medium", "中"), Map.entry("low", "低"));

    private final ObjectMapper mapper;
    private final HazardReviewRules reviewRules;

    public AiVisionResponseParser(ObjectMapper mapper, HazardReviewRules reviewRules) {
        this.mapper = mapper;
        this.reviewRules = reviewRules;
    }

    public AiAnalysisResult parse(String responseBody, String model) {
        String payload = extractModelContent(responseBody);
        JsonNode root = readJson(payload);
        JsonNode hazardsNode = root.get("hazards");
        if (hazardsNode == null || !hazardsNode.isArray()) {
            throw invalid("响应缺少 hazards 数组");
        }
        List<HazardItem> hazards = new ArrayList<>();
        for (int i = 0; i < hazardsNode.size(); i++) {
            hazards.add(reviewRules.apply(parseHazard(hazardsNode.get(i), i)));
        }
        return new AiAnalysisResult(hazards, AiResultSource.REAL, model, Instant.now(), responseBody);
    }

    /**
     * 解析整改照片复查响应。契约：
     * {"outcome":"PASSED|FAILED|INCONCLUSIVE","evidence":"...",
     *  "remainingHazards":[...同 hazards 契约...],"uncertainty":"...","suggestedAction":"..."}
     * outcome 缺失或非法取值一律 INVALID_RESPONSE，失败不会被当作通过。
     */
    public RemediationReview parseReview(String responseBody, String model) {
        String payload = extractModelContent(responseBody);
        JsonNode root = readJson(payload);
        ReviewOutcome outcome = outcome(root.get("outcome"));
        String evidence = optionalText(root, "evidence");
        String uncertainty = optionalText(root, "uncertainty");
        String suggestedAction = optionalText(root, "suggestedAction");
        List<HazardItem> remaining = new ArrayList<>();
        JsonNode hazardsNode = root.get("remainingHazards");
        if (hazardsNode != null && !hazardsNode.isNull()) {
            if (!hazardsNode.isArray()) {
                throw invalid("remainingHazards 必须是数组");
            }
            for (int i = 0; i < hazardsNode.size(); i++) {
                remaining.add(reviewRules.apply(parseHazard(hazardsNode.get(i), i)));
            }
        }
        if (outcome == ReviewOutcome.FAILED && remaining.isEmpty()) {
            throw invalid("outcome=FAILED 但 remainingHazards 缺少仍可见的风险证据");
        }
        return new RemediationReview(outcome, evidence, remaining, uncertainty, true,
                suggestedAction, AiResultSource.REAL, model, Instant.now(), responseBody);
    }

    private ReviewOutcome outcome(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw invalid("响应缺少合法的 outcome 字段");
        }
        try {
            return ReviewOutcome.valueOf(node.asText().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw invalid("outcome 取值不合法：" + node.asText());
        }
    }

    private String extractModelContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw invalid("响应体为空");
        }
        JsonNode root = readJson(responseBody);
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return stripCodeFence(content.asText());
            }
            throw invalid("choices[0].message.content 缺失或不是文本");
        }
        if (root.has("hazards")) {
            return stripCodeFence(responseBody);
        }
        throw invalid("响应缺少 choices 或 hazards 字段");
    }

    private HazardItem parseHazard(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw invalid("第 " + (index + 1) + " 条风险不是 JSON 对象");
        }
        String category = requiredText(node, "category", index);
        String location = requiredText(node, "location", index);
        String evidence = requiredText(node, "evidence", index);
        String suggestedAction = requiredText(node, "suggestedAction", index);
        Double confidence = confidence(node, index);
        String uncertainty = optionalText(node, "uncertainty");
        String riskLevel = riskLevel(node, index);
        boolean needsHumanReview = needsHumanReview(node, index);
        return new HazardItem(category, riskLevel, location, evidence, confidence, uncertainty,
                suggestedAction, needsHumanReview, false);
    }

    private String requiredText(JsonNode node, String field, int index) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("第 " + (index + 1) + " 条风险缺少字段 " + field);
        }
        return value.asText().trim();
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw invalid("字段 " + field + " 必须是字符串");
        }
        return value.asText().trim();
    }

    private Double confidence(JsonNode node, int index) {
        JsonNode value = node.get("confidence");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw invalid("第 " + (index + 1) + " 条风险的 confidence 必须是数字");
        }
        double confidence = value.asDouble();
        if (confidence < 0 || confidence > 1) {
            throw invalid("第 " + (index + 1) + " 条风险的 confidence 必须在 0 到 1 之间");
        }
        return confidence;
    }

    private boolean needsHumanReview(JsonNode node, int index) {
        JsonNode value = node.get("needsHumanReview");
        if (value == null || value.isNull()) {
            return true;
        }
        if (!value.isBoolean()) {
            throw invalid("第 " + (index + 1) + " 条风险的 needsHumanReview 必须是布尔值");
        }
        return value.asBoolean();
    }

    private String riskLevel(JsonNode node, int index) {
        JsonNode value = node.get("riskLevel");
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return "待评估";
        }
        if (!value.isTextual()) {
            throw invalid("第 " + (index + 1) + " 条风险的 riskLevel 必须是字符串");
        }
        String normalized = RISK_LEVELS.get(value.asText().trim().toLowerCase(Locale.ROOT));
        if (normalized == null) {
            throw invalid("第 " + (index + 1) + " 条风险的 riskLevel 取值不合法：" + value.asText());
        }
        return normalized;
    }

    private JsonNode readJson(String text) {
        if (text == null || text.isBlank()) {
            throw invalid("响应体为空");
        }
        try {
            return mapper.readTree(text);
        } catch (JsonProcessingException ex) {
            throw invalid("非法 JSON：" + ex.getOriginalMessage());
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline > -1) {
            trimmed = trimmed.substring(firstNewline + 1);
        }
        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence > -1) {
            trimmed = trimmed.substring(0, lastFence);
        }
        return trimmed.trim();
    }

    private AiVisionException invalid(String detail) {
        log.warn("视觉 API 响应校验失败：{}", detail);
        return new AiVisionException(AiErrorType.INVALID_RESPONSE, detail);
    }
}
