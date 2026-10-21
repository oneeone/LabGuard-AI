# 成员 A · 视觉 AI 配置与 API 契约（冻结版 v1.0）

冻结日期：2026 年 10 月 21 日 18:00（代码冻结前）
适用范围：`AiVisionService` 及其两个实现 `RealAiVisionService`（mode=real）、`DemoAiVisionService`（mode=demo）。
冻结规则：本文件所列请求契约、响应契约、错误类型和配置变量为 v1.0 最终版；后续变更必须先更新本文档并同步成员 B、C、D，再改代码。

> 集成备注（10/21 合并）：本文档中的 JSON 字段契约（`hazards`、`outcome` 等）在集成后保持不变；仅 Java 类名由 `Hazard` 改为 `HazardItem`（AI 分析 DTO），以避免与成员 B 的数据库实体 `Hazard` 撞名；落库时由 `DefaultInspectionService` 调用 `Hazard.fromAi(...)` 转换，风险等级“待评估”映射为 `RiskLevel.UNKNOWN`。`EnvConfig`（成员 D）使 `.env` 在启动前自动加载，优先级：系统环境变量 > `.env` > application.properties。

---

## 一、服务商、协议与模型

### 1.1 协议选型
真实实现对接 **OpenAI 兼容的 Chat Completions 协议**：

- 端点：`POST {VISION_API_BASE_URL}/chat/completions`（若 `VISION_API_BASE_URL` 已含 `/chat/completions` 则直接使用，不重复拼接）。
- 鉴权：请求头 `Authorization: Bearer {VISION_API_KEY}`。
- 内容类型：`Content-Type: application/json`，`Accept: application/json`。
- 图片传输：Base64 Data URL 内嵌于 `message.content` 的 `image_url` 部分，不依赖外网可访问的图片地址。

任何兼容该协议、且模型支持图像输入的服务商均可接入，仅需更换三个配置值，无需改代码。

### 1.2 候选服务商配置参考（按官方文档核实后填写）

| 服务商 | VISION_API_BASE_URL | 支持图像的模型示例（VISION_MODEL） |
| --- | --- | --- |
| 待团队选定 | `https://<provider>/v1` | 必须为官方文档明确支持 image_url 输入的模型 |

> ⚠️ 验收红线：未同时确定服务商、密钥、模型三项前，不得声称"真实 API 已接通"。当前开发机未配置密钥，页面与数据必须显示演示标识或配置缺失错误。

### 1.3 请求契约（冻结）

```json
{
  "model": "<VISION_MODEL>",
  "temperature": 0,
  "messages": [
    {"role": "system", "content": "<巡检或复查系统提示词>"},
    {"role": "user", "content": [
      {"type": "text", "text": "<用户指令 + 复查上下文>"},
      {"type": "image_url", "image_url": {"url": "data:image/png;base64,<图片Base64>"}}
    ]}
  ]
}
```

- `temperature=0`：保证结果可复现，降低模型输出漂移。
- 媒体类型：仅 `image/jpeg`（.jpg/.jpeg）与 `image/png`（.png）。

## 二、响应契约（冻结）

服务先读取 `choices[0].message.content`（兼容直接返回含 `hazards` 的裸 JSON），去除可能的 Markdown 代码围栏后做严格结构校验。**原始文本在校验通过前绝不成为工单事实**；原始响应全文保存在 `rawResponse` 字段供追溯。

### 2.1 风险巡检（analyze → AiAnalysisResult）

```json
{"hazards":[
  {"category":"风险类别","riskLevel":"高|中|低","location":"画面中的位置",
   "evidence":"图片中可见的证据","confidence":0.0,"uncertainty":"判断的不确定性",
   "suggestedAction":"整改建议","needsHumanReview":true}
]}
```

| 字段 | 必填 | 校验规则 |
| --- | --- | --- |
| hazards | 是 | 必须是数组；无风险时为 `[]` |
| category / location / evidence / suggestedAction | 是 | 非空字符串 |
| riskLevel | 否 | 仅接受 高/中/低（含 high/medium/low 等变体）；缺失记为"待评估"；非法取值 → INVALID_RESPONSE |
| confidence | 否 | 数字且 0~1；缺失为 null；非法 → INVALID_RESPONSE |
| uncertainty | 否 | 字符串；非空时强制人工核验 |
| needsHumanReview | 否 | 布尔值；缺失默认 true |

人工核验规则（`HazardReviewRules`，只增不减）：证据为空或短于 8 字符、置信度低于 `VISION_MIN_CONFIDENCE`（默认 0.6）、含不确定性说明、类别不在 `VISION_CATEGORY_WHITELIST` 白名单——任一命中即 `needsHumanReview=true` 并追加 `reviewReasons`；未知类别保留原文，绝不静默映射为已知类别。

### 2.2 整改复查（reviewRemediation → RemediationReview）

```json
{"outcome":"PASSED|FAILED|INCONCLUSIVE","evidence":"判断依据的可见证据",
 "remainingHazards":[ ...同 2.1 hazards 元素契约... ],
 "uncertainty":"判断的不确定性","suggestedAction":"下一步建议"}
```

安全规则（冻结，不可放宽）：
- `outcome` 缺失或非法取值 → INVALID_RESPONSE，**失败绝不当作通过**。
- `PASSED` 但 evidence 为空 → 自动降级为 `INCONCLUSIVE`，建议重新拍摄或现场核验。
- `FAILED` 但 `remainingHazards` 为空 → INVALID_RESPONSE。
- 复查结果 `needsHumanReview` 恒为 true：AI 只提供建议，关闭工单必须由人工复查决策（成员 B 的状态机执行）。
- 调用异常（超时/网络/HTTP 错误/非法 JSON）抛结构化异常，不返回任何默认结论。

### 2.3 边界（两种实现共同遵守）
- AI 只判断图片中可见线索，不断言设备通电、内部状态或化学品真实成分（系统提示词约束）。
- AI 服务不依赖、不写数据库、不修改工单状态（测试 A-19-20 以反射方式强制校验）。
- demo 模式不读取图片内容、零网络请求，来源恒为 DEMO；real 失败绝不回退 demo。

## 三、配置变量清单

| 变量 | 示例 | 要求 |
| --- | --- | --- |
| VISION_MODE | `real` / `demo`（默认 demo） | 页面和数据记录均显示结果来源 |
| VISION_API_BASE_URL | `https://<provider>/v1` | real 模式必填；官方 HTTPS 地址 |
| VISION_API_KEY | 仅环境变量注入 | **绝不写入代码、配置文件或 Git** |
| VISION_MODEL | 官方文档确认支持图像输入的模型 | real 模式必填 |
| VISION_CONNECT_TIMEOUT | `5s` | 可选，默认 5 秒 |
| VISION_READ_TIMEOUT | `30s` | 可选，默认 30 秒 |
| VISION_MIN_CONFIDENCE | `0.6` | 可选，低置信强制人工核验阈值（0~1） |
| VISION_CATEGORY_WHITELIST | 逗号分隔大写 | 可选，留空不限制类别 |

环境变量由 `EnvConfig` 自动读取 `.env`（也支持系统环境变量覆盖）。`.env.example` 是变量清单，复制为 `.env` 即用，`.env` 已在 `.gitignore` 中。

## 四、错误类型与处理（冻结）

| AiErrorType | 触发条件 | 用户可见提示 | 建议处理 |
| --- | --- | --- | --- |
| NOT_CONFIGURED | real 模式缺 BASE_URL/KEY/MODEL 任一（发起请求**前**拦截，HTTP 请求数 0） | 视觉 API 未配置完整，请配置 VISION_API_BASE_URL、VISION_API_KEY 和 VISION_MODEL。 | 补齐配置后重试 |
| UNSUPPORTED_IMAGE | 非 JPG/JPEG/PNG 或超过 10MB（发送前拒绝） | 仅支持 JPG、JPEG 或 PNG 图片，且单张大小不超过 10MB。 | 更换合规图片 |
| TIMEOUT | 连接/读取超时 | 视觉 API 请求超时，请检查网络或稍后重试。 | 重试或转人工处理 |
| AUTH_FAILED | HTTP 401 / 403 | 视觉 API 鉴权失败，请检查 API 密钥配置。 | 更正密钥 |
| RATE_LIMITED | HTTP 429 | 视觉 API 调用过于频繁，请稍后重试。 | 退避后重试 |
| SERVER_ERROR | HTTP 5xx | 视觉 API 服务暂时不可用，请稍后重试。 | 重试或演示前改用已录制素材 |
| NETWORK_ERROR | 连接失败/DNS/中断 | 无法连接视觉 API，请检查网络或 API 地址。 | 检查网络与地址 |
| REQUEST_FAILED | 空图片、上下文缺失、序列化失败等本地错误 | 视觉 API 请求失败，请稍后重试。 | 修正输入 |
| INVALID_RESPONSE | 空响应体、非法 JSON、缺字段、非法取值 | 视觉 API 返回格式无法识别，请保留原始错误并人工核验。 | 保留现场，人工处理 |

所有异常均携带 `type()`（分类）与 `detail()`（排查信息，不含密钥和图片内容），日志同样不输出密钥与图片内容。

## 五、冒烟测试步骤（真实配置就绪后执行）

1. 配置环境变量（PowerShell）：
   ```powershell
   $env:VISION_MODE = "real"
   $env:VISION_API_BASE_URL = "https://<provider>/v1"
   $env:VISION_API_KEY = "<密钥，仅本机使用>"
   $env:VISION_MODEL = "<支持图像的模型>"
   mvn spring-boot:run
   ```
2. 打开 http://localhost:8080，上传**已获授权**的实验室通道照片，确认：
   - 页面显示结果来源为 REAL 及模型名；
   - 每条风险含类别、等级、位置、证据、置信度/不确定性、整改建议；
   - 数据库巡检记录保存了结构化结果与原始响应。
3. 对已确认风险创建工单 → 提交整改照片 → 触发复查，确认 `outcome`、证据与 `needsHumanReview=true` 展示正确。
4. 异常路径验证（必做，证明不伪造结果）：
   - 清空 `VISION_API_KEY` 后调用 → 显示 NOT_CONFIGURED 提示，无伪造结果；
   - 填入错误密钥 → AUTH_FAILED；
   - 上传 .gif / 超 10MB 文件 → UNSUPPORTED_IMAGE。
5. 命令行快速验证（不经页面，可选）：
   ```powershell
   curl -s $env:VISION_API_BASE_URL/chat/completions -H "Authorization: Bearer $env:VISION_API_KEY" -H "Content-Type: application/json" -d "@smoke-request.json"
   ```

### 真实调用记录模板（执行后填写，附截图存档）

| 日期 | 服务商/模型 | 场景（巡检/复查） | 图片 | 结果来源 | 关键结果摘要 | 耗时 | 截图/记录 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| （待密钥配置后填写） | | | | | | | |

> 记录要求：至少 1 条巡检成功记录 + 1 条复查成功记录 + 1 条配置缺失报错记录，方可在验收中声称真实 API 已接通。演示当天提前完成真实调用录屏，防现场网络不稳定。

## 六、自动化测试清单（mvn test 全部通过）

| 编号段 | 文件 | 覆盖 |
| --- | --- | --- |
| A-01 | DemoAiVisionServiceTest | DEMO 来源与固定样例标记 |
| A-02~A-12 | RealAiVisionServiceTest | 配置缺失零请求、401/403/429/5xx、超时、断网、空响应、非法 JSON、成功链路请求构造 |
| A-10 | ImageInputTest | 格式/大小/空图片校验、媒体类型识别 |
| （解析器） | AiVisionResponseParserTest | hazards 结构、字段类型、riskLevel/confidence 规范化、代码围栏 |
| （核验规则） | HazardReviewRulesTest | 低置信、证据不足、不确定性、白名单只增不减 |
| A-19-10~21 / A-20-01~04 | RealAiVisionReviewTest、DemoAiVisionServiceTest | 复查上下文注入、PASSED/FAILED/INCONCLUSIVE、无证据降级、FAILED 缺证据拒绝、500/超时/断网/非法 JSON、无效图片零请求、AI 无工单依赖边界 |

## 七、限制与免责（对外口径）

- 首版仅覆盖可从静态图片观察的风险（优先：通道/安全出口被遮挡、物品影响通行）。
- AI 输出为辅助建议，不替代实验室安全人员的专业判断；所有结果需人工现场核验。
- 不提供实时视频、模型训练、多智能体协作；演示模式数据始终显式标记 DEMO。
