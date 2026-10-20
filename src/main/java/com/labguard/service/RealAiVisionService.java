package com.labguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labguard.model.AiAnalysisResult;
import com.labguard.model.RemediationContext;
import com.labguard.model.RemediationReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 真实视觉 API 实现（OpenAI 兼容的 /chat/completions 协议）。
 * <p>
 * 边界：
 * - 配置不完整时在发起请求前抛出 NOT_CONFIGURED，请求数为 0；
 * - 4xx/5xx、超时、网络错误、非法 JSON 都转成结构化 AiVisionException；
 * - 失败时只报错，绝不回退到演示数据或伪造成功结果；
 * - 日志不输出 API 密钥和图片内容。
 */
@Service
@ConditionalOnProperty(prefix = "labguard.ai", name = "mode", havingValue = "real")
public class RealAiVisionService implements AiVisionService {
    private static final Logger log = LoggerFactory.getLogger(RealAiVisionService.class);

    private static final String SYSTEM_PROMPT = """
            你是实验室安全巡检助手。只根据图片中可见的内容判断疑似风险，不判断设备是否通电、设备内部状态或化学品真实成分。
            只输出 JSON，不要输出解释或 Markdown 代码块，格式如下：
            {"hazards":[{"category":"风险类别","riskLevel":"高|中|低","location":"画面中的位置","evidence":"图片中可见的证据","confidence":0.0,"uncertainty":"判断的不确定性","suggestedAction":"整改建议","needsHumanReview":true}]}
            没有可见风险时输出 {"hazards":[]}。
            """;

    private static final String REVIEW_SYSTEM_PROMPT = """
            你是实验室安全整改复查助手。给定一张整改后的现场照片和原风险上下文，只根据图片中可见的内容判断整改效果。
            无法通过照片证明的事项必须输出 INCONCLUSIVE 并建议现场核验，不得给出确定结论。
            只输出 JSON，不要输出解释或 Markdown 代码块，格式如下：
            {"outcome":"PASSED|FAILED|INCONCLUSIVE","evidence":"判断依据的可见证据","remainingHazards":[{"category":"风险类别","riskLevel":"高|中|低","location":"画面中的位置","evidence":"图片中可见的证据","confidence":0.0,"uncertainty":"判断的不确定性","suggestedAction":"后续建议","needsHumanReview":true}],"uncertainty":"判断的不确定性","suggestedAction":"下一步建议"}
            规则：outcome=PASSED 时必须填写可见证据；outcome=FAILED 时 remainingHazards 必须非空；拿不准时选 INCONCLUSIVE。
            """;

    private final AiVisionProperties properties;
    private final AiVisionResponseParser parser;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    @Autowired
    public RealAiVisionService(AiVisionProperties properties, AiVisionResponseParser parser, ObjectMapper mapper) {
        this(properties, parser, mapper, HttpClient.newBuilder()
                .connectTimeout(properties.resolvedConnectTimeout())
                .build());
    }

    RealAiVisionService(AiVisionProperties properties, AiVisionResponseParser parser,
                        ObjectMapper mapper, HttpClient httpClient) {
        this.properties = properties;
        this.parser = parser;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    @Override
    public AiAnalysisResult analyze(ImageInput image) {
        String body = call(image, SYSTEM_PROMPT, "请分析这张实验室现场图片，并按 JSON 契约输出疑似风险。");
        return parser.parse(body, properties.getModel());
    }

    @Override
    public RemediationReview reviewRemediation(ImageInput remediationImage, RemediationContext context) {
        if (context == null) {
            throw new AiVisionException(AiErrorType.REQUEST_FAILED, "复查上下文不能为空");
        }
        String body = call(remediationImage, REVIEW_SYSTEM_PROMPT, reviewPromptText(context));
        return parser.parseReview(body, properties.getModel());
    }

    /** 发送请求并返回响应体；所有配置/网络/HTTP 错误在这里统一映射为结构化异常。 */
    private String call(ImageInput image, String systemPrompt, String userText) {
        if (!properties.complete()) {
            throw new AiVisionException(AiErrorType.NOT_CONFIGURED,
                    "缺少配置项：" + String.join("、", properties.missingConfiguration()));
        }
        URI endpoint = endpoint();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.resolvedReadTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(image, systemPrompt, userText), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            log.warn("视觉 API 请求超时，endpoint={}", endpoint);
            throw new AiVisionException(AiErrorType.TIMEOUT, ex.getMessage(), ex);
        } catch (IOException ex) {
            log.warn("视觉 API 连接失败，endpoint={}，类型={}", endpoint, ex.getClass().getSimpleName());
            throw new AiVisionException(AiErrorType.NETWORK_ERROR, ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiVisionException(AiErrorType.NETWORK_ERROR, "请求被中断", ex);
        }

        int status = response.statusCode();
        if (status / 100 == 2) {
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new AiVisionException(AiErrorType.INVALID_RESPONSE, "HTTP " + status + " 响应体为空");
            }
            log.info("视觉 API 调用成功，HTTP {}，模型={}", status, properties.getModel());
            return body;
        }

        log.warn("视觉 API 返回异常状态，HTTP {}", status);
        throw new AiVisionException(errorTypeFor(status), "HTTP " + status);
    }

    private String reviewPromptText(RemediationContext context) {
        StringBuilder text = new StringBuilder("请对这张整改后的照片做复查，并按 JSON 契约输出。需要验证的整改上下文：\n");
        text.append("工单编号：").append(context.orderNo().isBlank() ? "未提供" : context.orderNo()).append('\n');
        text.append("原风险类别：").append(context.originalCategory()).append('\n');
        if (!context.originalDescription().isBlank()) {
            text.append("原风险描述：").append(context.originalDescription()).append('\n');
        }
        text.append("整改目标：").append(context.remediationGoal()).append('\n');
        if (!context.originalSuggestedAction().isBlank()) {
            text.append("原整改建议：").append(context.originalSuggestedAction()).append('\n');
        }
        if (!context.remediationNote().isBlank()) {
            text.append("整改说明：").append(context.remediationNote()).append('\n');
        }
        text.append("请判断风险是否已被消除；无法从照片判断时输出 INCONCLUSIVE。");
        return text.toString();
    }

    private URI endpoint() {
        String base = properties.getBaseUrl().trim();
        String url = base.endsWith("/chat/completions")
                ? base
                : base.replaceAll("/+$", "") + "/chat/completions";
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                throw new AiVisionException(AiErrorType.NOT_CONFIGURED, "API 地址必须使用 http(s)");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new AiVisionException(AiErrorType.NOT_CONFIGURED, "API 地址不合法：" + base, ex);
        }
    }

    private String requestBody(ImageInput image, String systemPrompt, String userText) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", properties.getModel().trim());
        root.put("temperature", 0);
        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", userText);
        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        imagePart.putObject("image_url").put("url", dataUrl(image));

        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new AiVisionException(AiErrorType.REQUEST_FAILED, "请求体序列化失败", ex);
        }
    }

    private String dataUrl(ImageInput image) {
        return "data:" + image.mediaType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.bytes());
    }

    private AiErrorType errorTypeFor(int status) {
        if (status == 401 || status == 403) {
            return AiErrorType.AUTH_FAILED;
        }
        if (status == 429) {
            return AiErrorType.RATE_LIMITED;
        }
        if (status >= 500) {
            return AiErrorType.SERVER_ERROR;
        }
        return AiErrorType.REQUEST_FAILED;
    }
}
