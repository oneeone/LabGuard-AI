package com.labguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AiResultSource;
import com.labguard.model.Hazard;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealAiVisionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
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
    @DisplayName("A-02 real 模式缺少 API 地址：NOT_CONFIGURED 且不发请求")
    void missingBaseUrlFailsWithoutRequest() {
        RealAiVisionService service = service("", "test-key", Duration.ofSeconds(2));

        assertThatThrownBy(() -> service.analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.NOT_CONFIGURED);
                    assertThat(ex.getMessage())
                            .contains("VISION_API_BASE_URL", "VISION_API_KEY", "VISION_MODEL");
                });
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("A-03 real 模式缺少 API 密钥：NOT_CONFIGURED，HTTP 请求数为 0")
    void missingApiKeyFailsWithoutRequest() throws IOException {
        String baseUrl = startServer(200, "{}", Duration.ZERO);
        RealAiVisionService service = service(baseUrl, " ", Duration.ofSeconds(2));

        assertThatThrownBy(() -> service.analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.NOT_CONFIGURED);
                    assertThat(ex.getMessage()).contains("VISION_API_KEY");
                });
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("A-04 HTTP 401 返回 AUTH_FAILED")
    void unauthorizedFails() throws IOException {
        String baseUrl = startServer(401, "{\"error\":\"invalid api key\"}", Duration.ZERO);

        assertThatThrownBy(() -> service(baseUrl, "bad-key", Duration.ofSeconds(2)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.AUTH_FAILED);
                    assertThat(ex.getMessage()).isEqualTo("视觉 API 鉴权失败，请检查 API 密钥配置。");
                });
    }

    @Test
    @DisplayName("A-04 HTTP 403 返回 AUTH_FAILED")
    void forbiddenFails() throws IOException {
        String baseUrl = startServer(403, "{}", Duration.ZERO);

        assertThatThrownBy(() -> service(baseUrl, "bad-key", Duration.ofSeconds(2)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.AUTH_FAILED));
    }

    @Test
    @DisplayName("A-05 HTTP 500 返回 SERVER_ERROR，不返回演示结果")
    void serverErrorFailsWithoutDemoFallback() throws IOException {
        String baseUrl = startServer(500, "{\"error\":\"boom\"}", Duration.ZERO);

        assertThatThrownBy(() -> service(baseUrl, "test-key", Duration.ofSeconds(2)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.SERVER_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("视觉 API 服务暂时不可用，请稍后重试。");
                });
    }

    @Test
    @DisplayName("A-12 调用失败只抛异常，不产生任何分析结果")
    void failureNeverReturnsAnalysis() throws IOException {
        String baseUrl = startServer(503, "{}", Duration.ZERO);

        assertThatThrownBy(() -> service(baseUrl, "test-key", Duration.ofSeconds(2)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.SERVER_ERROR);
                    assertThat(ex.getMessage()).doesNotContain("演示");
                });
    }

    @Test
    @DisplayName("A-06 读取超时返回 TIMEOUT")
    void timeoutFails() throws IOException {
        String baseUrl = startServer(200, envelope(contract(validHazard())), Duration.ofMillis(1500));

        assertThatThrownBy(() -> service(baseUrl, "test-key", Duration.ofMillis(300)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.TIMEOUT);
                    assertThat(ex.getMessage()).isEqualTo("视觉 API 请求超时，请检查网络或稍后重试。");
                });
    }

    @Test
    @DisplayName("A-07 模型返回非法 JSON 时返回 INVALID_RESPONSE")
    void invalidJsonFails() throws IOException {
        String baseUrl = startServer(200, envelope("这不是 JSON"), Duration.ZERO);

        assertThatThrownBy(() -> service(baseUrl, "test-key", Duration.ofSeconds(2)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("A-11 合法真实响应返回 REAL 来源和完整字段，并校验请求构造")
    void validResponseSucceeds() throws IOException {
        String baseUrl = startServer(200, envelope(contract(validHazard())), Duration.ZERO);

        AiAnalysisResult result = service(baseUrl, "test-key", Duration.ofSeconds(2)).analyze(image());

        assertThat(result.source()).isEqualTo(AiResultSource.REAL);
        assertThat(result.hazards()).hasSize(1);
        Hazard hazard = result.hazards().get(0);
        assertThat(hazard.category()).isEqualTo("BLOCKED_EXIT");
        assertThat(hazard.riskLevel()).isEqualTo("高");
        assertThat(hazard.needsHumanReview()).isTrue();
        assertThat(hazard.demoData()).isFalse();
        assertThat(result.model()).isEqualTo("vision-test-model");
        assertThat(result.rawResponse()).contains("BLOCKED_EXIT");
        assertThat(result.analyzedAt()).isNotNull();

        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(lastAuthorization.get()).isEqualTo("Bearer test-key");
        assertThat(lastBody.get())
                .contains("\"model\":\"vision-test-model\"")
                .contains("\"type\":\"image_url\"")
                .contains("data:image/png;base64,");
    }

    @Test
    @DisplayName("HTTP 429 返回 RATE_LIMITED")
    void rateLimitedFails() throws IOException {
        String baseUrl = startServer(429, "{}", Duration.ZERO);

        assertThatThrownBy(() -> service(baseUrl, "test-key", Duration.ofSeconds(2)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.RATE_LIMITED));
    }

    @Test
    @DisplayName("HTTP 2xx 但响应体为空时返回 INVALID_RESPONSE")
    void emptyBodyFails() throws IOException {
        String baseUrl = startServer(200, "", Duration.ZERO);

        assertThatThrownBy(() -> service(baseUrl, "test-key", Duration.ofSeconds(2)).analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("连接失败返回 NETWORK_ERROR")
    void networkErrorFails() {
        RealAiVisionService service = service("http://127.0.0.1:1", "test-key", Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.analyze(image()))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.NETWORK_ERROR));
    }

    private RealAiVisionService service(String baseUrl, String apiKey, Duration readTimeout) {
        AiVisionProperties properties = new AiVisionProperties();
        properties.setMode("real");
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setModel("vision-test-model");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(readTimeout);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        return new RealAiVisionService(properties, new AiVisionResponseParser(mapper), mapper, client);
    }

    private ImageInput image() {
        return ImageInput.of("fake-image-bytes".getBytes(StandardCharsets.UTF_8), "lab.png");
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
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
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

    private String contract(ObjectNode... hazards) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode array = root.putArray("hazards");
        for (ObjectNode hazard : hazards) {
            array.add(hazard);
        }
        return mapper.writeValueAsString(root);
    }

    private String envelope(String content) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return mapper.writeValueAsString(root);
    }
}
