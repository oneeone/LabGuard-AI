package com.labguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labguard.model.AiResultSource;
import com.labguard.model.CheckStatus;
import com.labguard.model.Comparability;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 整改前后目标导向核验：真实服务 + mock HTTP。
 * 覆盖双图请求、逐证据点判定、可比性降级与各类失败边界。
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
    @DisplayName("A-19-11 复查请求同时携带原图、整改后图与原始证据上下文")
    void reviewRequestCarriesBothImagesAndOriginalEvidence() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("COMPARABLE", "出口前地面可见，无堆放物",
                List.of(check("纸箱堆放在安全出口前", "RESOLVED", "出口前地面空置", "")))));

        RemediationReview review = service(baseUrl).reviewRemediation(originalImage(), remediationImage(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.PASSED);
        assertThat(requestCount.get()).isEqualTo(1);
        String body = lastBody.get();
        assertThat(body)
                .contains("BLOCKED_EXIT")
                .contains("移除杂物并保持安全出口通道畅通")
                .contains("WO-2026-001")
                .contains("纸箱堆放在安全出口前")
                .contains("实验室门口")
                .contains("图1：原始隐患证据照片")
                .contains("图2：整改后照片");
        assertThat(countOf(body, "data:image/png;base64,")).isEqualTo(2);
    }

    @Test
    @DisplayName("A-19-12 逐点全部 RESOLVED 且有可见依据时给出 PASSED，仍需人工确认")
    void allChecksResolvedYieldsPassed() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("COMPARABLE", "通道恢复畅通，地面无堆放物",
                List.of(check("纸箱堆放在安全出口前", "RESOLVED", "出口前地面空置", "")))));

        RemediationReview review = service(baseUrl).reviewRemediation(originalImage(), remediationImage(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.PASSED);
        assertThat(review.source()).isEqualTo(AiResultSource.REAL);
        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.checks()).hasSize(1);
        assertThat(review.checks().get(0).status()).isEqualTo(CheckStatus.RESOLVED);
        assertThat(review.checks().get(0).visibleEvidence()).contains("空置");
        assertThat(review.comparability()).isEqualTo(Comparability.COMPARABLE);
    }

    @Test
    @DisplayName("A-19-13 任一核验点 UNRESOLVED 即判定 FAILED，不能被整体证据掩盖")
    void anyUnresolvedYieldsFailed() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("COMPARABLE", "出口前仍可见纸箱",
                List.of(check("纸箱堆放在安全出口前", "UNRESOLVED", "出口左侧仍有一个纸箱", "清理不彻底")))));

        RemediationReview review = service(baseUrl).reviewRemediation(originalImage(), remediationImage(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.FAILED);
        assertThat(review.checks().get(0).status()).isEqualTo(CheckStatus.UNRESOLVED);
        assertThat(review.suggestedAction()).contains("人工确认");
    }

    @Test
    @DisplayName("A-19-14 存在 INCONCLUSIVE 核验点时整体结论为 INCONCLUSIVE")
    void anyInconclusiveYieldsInconclusive() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("PARTIAL", "出口区域部分可见",
                List.of(check("纸箱堆放在安全出口前", "INCONCLUSIVE", "", "拍摄角度被门框遮挡")))));

        RemediationReview review = service(baseUrl).reviewRemediation(originalImage(), remediationImage(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.INCONCLUSIVE);
        assertThat(review.needsHumanReview()).isTrue();
        assertThat(review.checks().get(0).reason()).contains("遮挡");
    }

    @Test
    @DisplayName("A-19-15 视角不可比时即使逐点全部 RESOLVED 也强制 INCONCLUSIVE")
    void notComparableForcesInconclusive() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("NOT_COMPARABLE", "两张照片视角不同",
                List.of(check("纸箱堆放在安全出口前", "RESOLVED", "画面中未看到纸箱", "")))));

        RemediationReview review = service(baseUrl).reviewRemediation(originalImage(), remediationImage(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.INCONCLUSIVE);
        assertThat(review.comparability()).isEqualTo(Comparability.NOT_COMPARABLE);
        assertThat(review.suggestedAction()).doesNotContain("通过");
    }

    @Test
    @DisplayName("A-19-16 PASSED 却没有任何可见证据时降级为 INCONCLUSIVE")
    void passedWithoutEvidenceDowngrades() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("COMPARABLE", "  ",
                List.of(check("纸箱堆放在安全出口前", "RESOLVED", "画面空置", "")))));

        RemediationReview review = service(baseUrl).reviewRemediation(originalImage(), remediationImage(), context());

        assertThat(review.outcome()).isEqualTo(ReviewOutcome.INCONCLUSIVE);
        assertThat(review.needsHumanReview()).isTrue();
    }

    @Test
    @DisplayName("A-19-17 缺少 checks 逐点核验数组时返回 INVALID_RESPONSE")
    void missingChecksRejected() throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("comparability", "COMPARABLE");
        root.put("evidence", "看起来已清理");
        String baseUrl = startServer(200, envelope(mapper.writeValueAsString(root)));

        assertThatThrownBy(() -> service(baseUrl).reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("A-19-18 RESOLVED 缺少 visibleEvidence 或 status 非法时拒绝")
    void invalidCheckRejected() throws IOException {
        String noEvidence = startServer(200, envelope(reviewContract("COMPARABLE", "已清理",
                List.of(check("纸箱堆放在安全出口前", "RESOLVED", "", "")))));
        assertThatThrownBy(() -> service(noEvidence).reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));

        String badStatus = startServer(200, envelope(reviewContract("COMPARABLE", "已清理",
                List.of(check("纸箱堆放在安全出口前", "MAYBE", "看起来没了", "")))));
        assertThatThrownBy(() -> service(badStatus).reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("A-19-19 视图不可比之外，非法 comparability 或非法 JSON 同样拒绝")
    void invalidComparabilityOrJsonRejected() throws IOException {
        String badJson = startServer(200, envelope("not-json"));
        assertThatThrownBy(() -> service(badJson).reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));

        String badComparability = startServer(200, envelope(reviewContract("SAME_ANGLE", "证据",
                List.of(check("纸箱", "RESOLVED", "空置", "")))));
        assertThatThrownBy(() -> service(badComparability)
                .reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("A-19-20 配置缺失时复查 NOT_CONFIGURED，HTTP 请求数为 0")
    void reviewWithoutConfigSendsNoRequest() {
        RealAiVisionService service = service("");

        assertThatThrownBy(() -> service.reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.NOT_CONFIGURED);
                    assertThat(ex.getMessage()).doesNotContain("PASSED");
                });
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("A-20-01 任一张复查图片格式不支持时发送前拒绝，HTTP 请求数为 0")
    void reviewRejectsUnsupportedImageWithoutRequest() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("COMPARABLE", "证据",
                List.of(check("纸箱", "RESOLVED", "空置", "")))));

        assertThatThrownBy(() -> service(baseUrl).reviewRemediation(originalImage(),
                ImageInput.of("fake".getBytes(StandardCharsets.UTF_8), "after.webp"), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.UNSUPPORTED_IMAGE));
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("A-20-02 复查图片为空或超出 10MB 时被拒绝，不发起调用")
    void reviewRejectsEmptyAndOversizedImage() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("COMPARABLE", "证据",
                List.of(check("纸箱", "RESOLVED", "空置", "")))));
        RealAiVisionService service = service(baseUrl);
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = 'x';

        assertThatThrownBy(() -> service.reviewRemediation(originalImage(),
                ImageInput.of(new byte[0], "after.png"), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.REQUEST_FAILED));
        assertThatThrownBy(() -> service.reviewRemediation(originalImage(),
                ImageInput.of(oversized, "after.png"), context()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.UNSUPPORTED_IMAGE));
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("A-20-03 复查读取超时返回 TIMEOUT，不会返回 PASSED")
    void reviewTimeoutFails() throws IOException {
        String baseUrl = startServer(200, envelope(reviewContract("COMPARABLE", "通道已恢复畅通",
                List.of(check("纸箱", "RESOLVED", "空置", "")))), Duration.ofMillis(1500));

        assertThatThrownBy(() -> service(baseUrl, Duration.ofMillis(300))
                .reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.TIMEOUT);
                    assertThat(ex.getMessage()).isEqualTo("视觉 API 请求超时，请检查网络或稍后重试。");
                });
    }

    @Test
    @DisplayName("A-20-04 复查网络连接失败返回 NETWORK_ERROR，不伪造结果")
    void reviewNetworkFailureFails() {
        RealAiVisionService service = service("http://127.0.0.1:1");

        assertThatThrownBy(() -> service.reviewRemediation(originalImage(), remediationImage(), context()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.NETWORK_ERROR);
                    assertThat(ex.getMessage()).doesNotContain("PASSED");
                });
    }

    @Test
    @DisplayName("A-19-21 边界：AI 服务不依赖工单服务，不直接修改工单状态")
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
    @DisplayName("A-19-22 复查上下文缺少原类别、整改目标或原始证据时直接拒绝")
    void reviewContextValidated() {
        assertThatThrownBy(() -> new RemediationContext("WO-1", " ", "目标"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RemediationContext("WO-1", "BLOCKED_EXIT", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RemediationContext("WO-1", "BLOCKED_EXIT", "", "目标", "", "", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RealAiVisionService service(String baseUrl) {
        return service(baseUrl, Duration.ofSeconds(2));
    }

    private RealAiVisionService service(String baseUrl, Duration readTimeout) {
        AiVisionProperties properties = new AiVisionProperties();
        properties.setMode("real");
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setModel("vision-test-model");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(readTimeout);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        return new RealAiVisionService(properties,
                new AiVisionResponseParser(mapper, new HazardReviewRules(properties)), mapper, client);
    }

    private ImageInput originalImage() {
        return ImageInput.of("original-image-bytes".getBytes(StandardCharsets.UTF_8), "before.png");
    }

    private ImageInput remediationImage() {
        return ImageInput.of("fake-image-bytes".getBytes(StandardCharsets.UTF_8), "after.png");
    }

    private RemediationContext context() {
        return new RemediationContext("WO-2026-001", "BLOCKED_EXIT", "安全出口被杂物阻挡",
                "移除杂物并保持安全出口通道畅通", "移除障碍并巡检", "已安排值日组清理",
                "纸箱堆放在安全出口前", "实验室门口");
    }

    private ObjectNode check(String point, String status, String visibleEvidence, String reason) {
        ObjectNode node = mapper.createObjectNode();
        node.put("point", point);
        node.put("status", status);
        node.put("visibleEvidence", visibleEvidence);
        node.put("reason", reason);
        return node;
    }

    private String reviewContract(String comparability, String evidence, List<ObjectNode> checks,
                                  ObjectNode... remaining) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("comparability", comparability);
        root.put("evidence", evidence);
        root.put("uncertainty", "仅依据当前视角判断");
        root.put("suggestedAction", "请人工确认后关闭工单");
        ArrayNode checksNode = root.putArray("checks");
        checks.forEach(checksNode::add);
        ArrayNode hazards = root.putArray("remainingHazards");
        for (ObjectNode hazard : remaining) {
            hazards.add(hazard);
        }
        return mapper.writeValueAsString(root);
    }

    private String envelope(String content) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return mapper.writeValueAsString(root);
    }

    private int countOf(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private String startServer(int status, String body) throws IOException {
        return startServer(status, body, Duration.ZERO);
    }

    private String startServer(int status, String body, Duration delay) throws IOException {
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
            sleep(delay);
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

    private void sleep(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
