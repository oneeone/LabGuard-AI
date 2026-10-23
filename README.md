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

应用启动时会确保 `data/` 目录存在，并自动创建 `data/labguard.db`。工程的 `EnvConfig`（成员 D，经 `META-INF/spring.factories` 注册）会在启动前自动加载项目根目录的 `.env`；优先级：系统环境变量 > `.env` > application.properties 默认值。

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

## Deployment
可运行 JAR 由 `mvn package` 生成。程序包制作、Windows/Linux 启动脚本、Docker 与云平台部署步骤见 [DEPLOY.md](DEPLOY.md)；仓库根目录已提供 `Dockerfile`。

### 程序包获取方式（评委请先读这里）

`dist/` 在 `.gitignore` 中被忽略（Spring Initializr 生成的 NetBeans 段落含 `/dist/`），所以**克隆仓库不会得到可运行程序包**。获取方式：

| 来源 | 位置 |
| --- | --- |
| GitHub Release 附件（首选） | <https://github.com/oneeone/LabGuard-AI/releases/latest> |
| 赛事提交系统附件 | 与 ZIP 同源，下载后直接解压运行 |
| 从源码构建（无需下载） | `mvn clean package` → `java -jar target/labguard-ai-0.1.0-SNAPSHOT.jar` |

包信息：`LabGuard-AI-v1.0.0.zip`，版本 `v1.0.0`，约 35 MB（36,970,515 字节）。解压后得到 `LabGuard-AI-v1.0.0/` 目录，双击 `start-windows.bat`（或执行 `./start-linux.sh`）后访问 <http://localhost:8080>；默认 `demo` 模式，离线可用、无需密钥。包内不含 `.env`、`data/labguard.db` 与 `uploads/` 图片，仅有 `.env.example` 和两个空占位目录。

AI 模块测试 A-01~A-20 使用 JDK 内置 HttpServer 模拟服务商响应，不调用外部 API，也不需要真实密钥。完整的契约、服务商选型、错误清单与冒烟测试步骤见 `_docs/成员A-视觉AI配置与API契约说明.md`。

## AI vision service
`AiVisionService` 是接口，业务层只依赖接口，不直接依赖 HTTP 客户端。

- `DemoAiVisionService`（`VISION_MODE=demo`，默认）：返回固定样例，来源固定为 `DEMO`，页面显示 `AI result source: DEMO` 与“演示数据：未调用真实视觉 API”。
- `RealAiVisionService`（`VISION_MODE=real`）：按 OpenAI 兼容的 `POST {base}/chat/completions` 发送 base64 图片（`image_url`），使用 Bearer 鉴权。
  - 缺少 `VISION_API_BASE_URL`、`VISION_API_KEY` 或 `VISION_MODEL` 时，在发起请求前抛出 `NOT_CONFIGURED`（HTTP 请求数为 0）。
  - 401/403 → `AUTH_FAILED`，429 → `RATE_LIMITED`，5xx → `SERVER_ERROR`，超时 → `TIMEOUT`，连接失败 → `NETWORK_ERROR`，非法或缺少字段的 JSON → `INVALID_RESPONSE`。
  - 失败只抛结构化异常，不会回退到演示数据，也不会伪造成功结果。
- `AiVisionResponseParser` 严格校验模型输出，解析结果为 `HazardItem`（AI 分析 DTO，不持有数据库状态）：`hazards` 必须是数组；`category`、`location`、`evidence`、`suggestedAction` 必填；`confidence` 必须是 0~1；`needsHumanReview` 必须是布尔值；`riskLevel` 仅接受高/中/低（缺失记为“待评估”）。落库时由巡检编排服务转换为 B 的 `Hazard` 实体（初始 `PENDING_CONFIRM`，等级“待评估”记为 `UNKNOWN`）。

### 整改照片复查（reviewRemediation）
`AiVisionService.reviewRemediation(image, RemediationContext)` 结合原风险上下文分析整改后图片，返回 `RemediationReview`：
- `outcome` 仅接受 `PASSED|FAILED|INCONCLUSIVE`；缺失或非法取值 → `INVALID_RESPONSE`，失败不会被当作通过。
- `PASSED` 但证据为空自动降级为 `INCONCLUSIVE`；`FAILED` 但 `remainingHazards` 为空 → `INVALID_RESPONSE`。
- 复查结果 `needsHumanReview` 恒为 `true`：AI 只给建议，关闭工单由人工复查决策。
- demo 模式复查固定返回 `INCONCLUSIVE` + `DEMO` 来源，绝不伪装成通过。
- 图片无效、超时、断网、5xx、非法 JSON 均抛结构化异常且零伪造结果（测试 A-20-01~04）。

配置变量：`VISION_MODE`、`VISION_API_BASE_URL`、`VISION_API_KEY`、`VISION_MODEL`、`VISION_CONNECT_TIMEOUT`、`VISION_READ_TIMEOUT`，见 `.env.example`。`VISION_API_BASE_URL` 可直接写完整 `/chat/completions` 地址，也可只写基础地址（服务会自动补全路径）。

## Work order states
隐患确认：`PENDING_CONFIRM` → `CONFIRMED` / `REJECTED`（只有已确认隐患可建工单，一隐患一工单）

工单：`PENDING_REMEDIATION` → `PENDING_REVIEW` → `CLOSED`；复查不通过退回 `PENDING_REMEDIATION`。

状态转换由后端状态机校验（非法跳转拒绝），每次变更写入 `audit_event` 审计时间线。

## Project structure
```text
src/main/java/com/labguard/
├── LabGuardApplication.java      # D：启动类（@ConfigurationPropertiesScan）
├── config/                       # D：EnvConfig(.env 自动加载)、GlobalExceptionHandler
├── controller/                   # C：巡检上传/分析结果/隐患审核/工单/整改/复查页面路由
├── web/                          # C：表单校验、视图对象( HazardCard 等)、上传映射
├── model/                        # A：AiAnalysisResult/HazardItem/AiResultSource/Remediation*；B：Inspection/Hazard/WorkOrder/AuditEvent 实体与状态枚举
├── repository/                   # B：Spring JDBC 仓储（五表）
├── workflow/                     # B：隐患/工单状态机与业务异常
└── service/                      # A：视觉 AI 双实现与响应校验；B：业务服务；C：巡检编排与本地存储
src/main/resources/
├── application.properties        # SQLite + labguard.ai.* + 环境变量覆盖
├── schema.sql                    # B：五表终版（inspection/hazard/work_order/audit_event/ai_review）
├── static/ + templates/          # C：页面与样式
└── META-INF/spring.factories     # D：.env 加载器注册
src/test/java/com/labguard/       # A/B/C/D 单元 + MVC + 持久化测试（共 167 个）
pom.xml                           # Boot 3.5.x + SQLite + Maven
```

## Safety and privacy
AI results are decision support, not a replacement for professional inspection. Human review and on-site verification are required. Never commit API keys or sensitive laboratory images to Git.
