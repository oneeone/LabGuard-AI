# LabGuard AI 🛡️

**An AI-Powered Laboratory Safety Inspection Agent**

LabGuard AI connects visual hazard analysis with a human-reviewed remediation workflow: image upload → hazard review → work-order creation → corrective action → reinspection → closure.

## Stack
- Java 17
- Spring Boot 3.5.x
- Spring MVC + Thymeleaf
- SQLite + Spring JDBC
- Maven
- JUnit 5

## Prerequisites
- JDK 17+
- Maven 3.9+ (or use Maven Wrapper if your team adds one)

## Run locally

### Windows PowerShell
```powershell
# 演示模式（默认）：不调用真实视觉 API
$env:VISION_MODE = "demo"
mvn spring-boot:run
```

### macOS / Linux
```bash
VISION_MODE=demo mvn spring-boot:run
```

Open http://localhost:8080.

The application creates `data/labguard.db` automatically. Spring Boot 不会自动读取 `.env` 文件，`.env.example` 只是变量清单；请在终端或 IDE 运行配置里设置环境变量。

### Real vision API (optional)
```powershell
$env:VISION_MODE = "real"
$env:VISION_API_BASE_URL = "https://<provider>/v1"
$env:VISION_API_KEY = "<key>"
$env:VISION_MODEL = "<vision-model>"
mvn spring-boot:run
```

## Build and test
```bash
mvn test
mvn package
java -jar target/labguard-ai-0.1.0-SNAPSHOT.jar
```

AI 模块测试 A-01~A-20 使用 JDK 内置 HttpServer 模拟服务商响应，不调用外部 API，也不需要真实密钥。完整的契约、服务商选型、错误清单与冒烟测试步骤见 `_docs/成员A-视觉AI配置与API契约说明.md`。

## AI vision service
`AiVisionService` 是接口，业务层只依赖接口，不直接依赖 HTTP 客户端。

- `DemoAiVisionService`（`VISION_MODE=demo`，默认）：返回固定样例，来源固定为 `DEMO`，页面显示 `AI result source: DEMO` 与“演示数据：未调用真实视觉 API”。
- `RealAiVisionService`（`VISION_MODE=real`）：按 OpenAI 兼容的 `POST {base}/chat/completions` 发送 base64 图片（`image_url`），使用 Bearer 鉴权。
  - 缺少 `VISION_API_BASE_URL`、`VISION_API_KEY` 或 `VISION_MODEL` 时，在发起请求前抛出 `NOT_CONFIGURED`（HTTP 请求数为 0）。
  - 401/403 → `AUTH_FAILED`，429 → `RATE_LIMITED`，5xx → `SERVER_ERROR`，超时 → `TIMEOUT`，连接失败 → `NETWORK_ERROR`，非法或缺少字段的 JSON → `INVALID_RESPONSE`。
  - 失败只抛结构化异常，不会回退到演示数据，也不会伪造成功结果。
- `AiVisionResponseParser` 严格校验模型输出：`hazards` 必须是数组；`category`、`location`、`evidence`、`suggestedAction` 必填；`confidence` 必须是 0~1；`needsHumanReview` 必须是布尔值；`riskLevel` 仅接受高/中/低（缺失记为“待评估”）。

### 整改照片复查（reviewRemediation）
`AiVisionService.reviewRemediation(image, RemediationContext)` 结合原风险上下文分析整改后图片，返回 `RemediationReview`：
- `outcome` 仅接受 `PASSED|FAILED|INCONCLUSIVE`；缺失或非法取值 → `INVALID_RESPONSE`，失败不会被当作通过。
- `PASSED` 但证据为空自动降级为 `INCONCLUSIVE`；`FAILED` 但 `remainingHazards` 为空 → `INVALID_RESPONSE`。
- 复查结果 `needsHumanReview` 恒为 `true`：AI 只给建议，关闭工单由人工复查决策。
- demo 模式复查固定返回 `INCONCLUSIVE` + `DEMO` 来源，绝不伪装成通过。
- 图片无效、超时、断网、5xx、非法 JSON 均抛结构化异常且零伪造结果（测试 A-20-01~04）。

配置变量：`VISION_MODE`、`VISION_API_BASE_URL`、`VISION_API_KEY`、`VISION_MODEL`、`VISION_CONNECT_TIMEOUT`、`VISION_READ_TIMEOUT`，见 `.env.example`。`VISION_API_BASE_URL` 可直接写完整 `/chat/completions` 地址，也可只写基础地址（服务会自动补全路径）。

## Work order states
`PENDING_REMEDIATION` → `PENDING_REVIEW` → `CLOSED`

A failed review returns the work order to `PENDING_REMEDIATION`.

## Project structure
```text
src/main/java/com/labguard/
├── LabGuardApplication.java
├── controller/HomeController.java
├── model/
│   ├── AiAnalysisResult.java
│   ├── AiResultSource.java
│   ├── Hazard.java
│   ├── RemediationContext.java
│   ├── RemediationReview.java
│   ├── ReviewOutcome.java
│   └── WorkOrder.java
└── service/
    ├── AiErrorType.java
    ├── AiVisionException.java
    ├── AiVisionProperties.java
    ├── AiVisionResponseParser.java
    ├── AiVisionService.java
    ├── DemoAiVisionService.java
    ├── HazardReviewRules.java
    ├── ImageInput.java
    ├── RealAiVisionService.java
    └── WorkOrderService.java
src/main/resources/
├── application.properties
├── schema.sql
└── templates/index.html
src/test/java/com/labguard/
├── LabGuardApplicationTests.java
└── service/
    ├── AiVisionResponseParserTest.java
    ├── DemoAiVisionServiceTest.java
    ├── HazardReviewRulesTest.java
    ├── ImageInputTest.java
    ├── RealAiVisionReviewTest.java
    └── RealAiVisionServiceTest.java
pom.xml
```

## Safety and privacy
AI results are decision support, not a replacement for professional inspection. Human review and on-site verification are required. Never commit API keys or sensitive laboratory images to Git.
