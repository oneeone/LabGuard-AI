package com.labguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labguard.model.AiResultSource;
import com.labguard.model.RemediationContext;
import com.labguard.model.RemediationReview;
import com.labguard.model.ReviewOutcome;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 10 月 19 日：整改照片复查链路测试（真实服务 + mock HTTP）。
 */
class RealAiVisionReviewTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("A-19-11 复查请求体包含原风险类别和整改目标")
    void reviewRequestCarriesContext() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("PASSED", "通道已恢复畅通，无堆放物")));

        service(baseUrl).reviewRemediation(image(), context());

        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(lastBody.get())
                .contains("BLOCKED_EXIT")
                .contains("移除杂物并保持安全出口通道畅通")
                .contains("WO-2026-001")
                .contains("data:image/png;base64,");
    }

    @Test
    @DisplayName("A-19-12 复查通过建议返回 PASSED，但仍保留人工确认标记和 REAL 来源")
    void passedKeepsHumanReview() throws IOException {
        String baseUrl = startServer(200,
                envelope(reviewContract("PASSED", "通道已恢复畅通，地面无堆放物")));

        RemediationReview review = service(baseUrl).reviewRemediation(image(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.PASSED);
        assertThat(review.source()).isEqualTo(AiResultSource.REAL);
        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.evidence()).contains("畅通");
        assertThat(review.analyzedAt()).isNotNull();
    }

    @Test
    @DisplayName("A-19-13 PASSED 但证据为空时降级为 INCONCLUSIVE，失败不当作通过")
    void passedWithoutEvidenceDowngrades() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("PASSED", "  ")));

        RemediationReview review = service(baseUrl).reviewRemediation(image(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.INCONCLUSIVE);
        assertThat(review.needsHumanReview()).isTrue();
    }

    @Test
    @DisplayName("A-19-14 复查未通过返回 FAILED，并包含剩余风险证据")
    void failedCarriesRemainingHazards() throws IOException {
        ObjectNode remaining = mapper.createObjectNode();
        remaining.put("category", "BLOCKED_EXIT");
        remaining.put("riskLevel", "高");
        remaining.put("location", "画面右侧出口前");
        remaining.put("evidence", "仍可见一个纸箱占用出口通道");
        remaining.put("confidence", 0.9);
        remaining.put("uncertainty", "");
        remaining.put("suggestedAction", "搬离剩余纸箱");
        remaining.put("needsHumanReview", true);
        String baseUrl = startServer(200,
                envelope(reviewContract("FAILED", "出口前仍有杂物", remaining)));

        RemediationReview review = service(baseUrl).reviewRemediation(image(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.FAILED);
        assertThat(review.remainingHazards()).hasSize(1);
        assertThat(review.remainingHazards().get(0).evidence()).contains("纸箱");
        assertThat(review.source()).isEqualTo(AiResultSource.REAL);
    }

    @Test
    @DisplayName("A-19-15 FAILED 但缺少剩余风险证据时返回 INVALID_RESPONSE")
    void failedWithoutRemainingHazardsRejected() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("FAILED", "仍有风险")));

        assertThatThrownBy(() -> service(baseUrl).reviewRemediation(image(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("A-19-16 证据不足返回 INCONCLUSIVE 并建议重新拍摄或现场核验")
    void inconclusiveSupported() throws IOException {
        String baseUrl = startServer(200, envelope(
                reviewContract("INCONCLUSIVE", "画面角度无法看到出口全貌")));

        RemediationReview review = service(baseUrl).reviewRemediation(image(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.INCONCLUSIVE);
        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.source()).isEqualTo(AiResultSource.REAL);
    }

    @Test
    @DisplayName("A-19-17 复查 API 返回 500 时抛异常，不会返回 PASSED")
    void serverErrorNeverPassed() throws IOException {
        String baseUrl = startServer(500, "{\"error\":\"boom\"}");

        assertThatThrownBy(() -> service(baseUrl).reviewRemediation(image(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.SERVER_ERROR));
    }

    @Test
    @DisplayName("A-19-18 复查返回非法 JSON 或非法 outcome 时返回 INVALID_RESPONSE")
    void invalidReviewResponseRejected() throws IOException {
        String badJson = startServer(200, envelope("not-json"));
        assertThatThrownBy(() -> service(badJson).reviewRemediation(image(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));

        String badOutcome = startServer(200, envelope(reviewContract("MAYBE", "证据")));
        assertThatThrownBy(() -> service(badOutcome).reviewRemediation(image(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("A-19-19 配置缺失时复查同样 NOT_CONFIGURED，HTTP 请求数为 0")
    void reviewWithoutConfigSendsNoRequest() {
        RealAiVisionService service = service("");

        assertThatThrownBy(() -> service.reviewRemediation(image(), context()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.NOT_CONFIGURED);
                    assertThat(ex.getMessage()).doesNotContain("PASSED");
                });
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("A-19-20 边界：AI 服务不依赖工单服务，不直接修改工单状态")
    void aiServiceHasNoWorkOrderDependency() {
        for (Class<?> type : new Class<?>[]{RealAiVisionService.class, DemoAiVisionService.class,
                AiVisionResponseParser.class, HazardReviewRules.class}) {
            assertThat(Arrays.stream(type.getDeclaredFields()).map(field -> field.getType().getName())
                    .noneMatch(name -> name.contains("WorkOrder")))
                    .as(type.getSimpleName() + " 不应依赖工单服务")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("A-19-21 复查上下文缺少原类别或整改目标时直接拒绝")
    void reviewContextValidated() {
        assertThatThrownBy(() -> new RemediationContext("WO-1", " ", "目标"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RemediationContext("WO-1", "BLOCKED_EXIT", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RealAiVisionService service(String baseUrl) {
        AiVisionProperties properties = new AiVisionProperties();
        properties.setMode("real");
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setModel("vision-test-model");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        return new RealAiVisionService(properties,
                new AiVisionResponseParser(mapper, new HazardReviewRules(properties)), mapper, client);
    }

    private ImageInput image() {
        return ImageInput.of("fake-image-bytes".getBytes(StandardCharsets.UTF_8), "after.png");
    }

    private RemediationContext context() {
        return new RemediationContext("WO-2026-001", "BLOCKED_EXIT", "安全出口被杂物阻挡",
                "移除杂物并保持安全出口通道畅通", "移除障碍并巡检", "已安排值日组清理");
    }

    private String reviewContract(String outcome, String evidence, ObjectNode... remaining) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("outcome", outcome);
        root.put("evidence", evidence);
        root.put("uncertainty", "仅依据当前视角判断");
        root.put("suggestedAction", "请人工确认后关闭工单");
        ArrayNode array = root.putArray("remainingHazards");
        for (ObjectNode hazard : remaining) {
            array.add(hazard);
        }
        return mapper.writeValueAsString(root);
    }

    private String envelope(String content) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return mapper.writeValueAsString(root);
    }

    private String startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, payload.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(payload);
                }
            } catch (IOException ignored) {
                // 客户端超时后连接已关闭，写入失败可忽略
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
