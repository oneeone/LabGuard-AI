# LabGuard AI — B 模块设计契约

- 负责人：成员 B（数据库与工作流）
- 版本：v1.0
- 对应任务：10/16 需求冻结与工程初始化
- 状态：待 A / C / D 确认后冻结
- 约束：本文档冻结后，任何字段变更须先改本文档，再同步相关成员

## 0. 对 C 模块契约"待确认事项"的答复

| C 契约编号 | 事项 | B 的答复 |
|---|---|---|
| #1 | Hazard / WorkOrder 状态是否按 5.1 拆分 | **同意拆分**。`Hazard` 维护人工确认状态，`WorkOrder` 维护流转状态，两者互不嵌套。工单创建后不存在"待确认"状态。 |
| #6 | AuditEvent 是否在详情页展示 | **建议展示**，以时间线形式放在工单详情页底部。数据由 `AuditEventRepository.findByEntity` 提供。 |
| #8 | 是否需要 `riskLevelSource` 字段 | **需要**。取值 `RULE`（规则建议）/ `MANUAL`（人工调整），用于区分"建议风险等级"和"人工最终等级"的溯源。 |

## 1. 范围与边界

### 1.1 本文档覆盖

- SQLite 四表结构：`inspection`、`hazard`、`work_order`、`audit_event`
- 数据模型与 Repository 层的读写约定
- Hazard 人工确认、WorkOrder 状态机的合法转换与前置条件
- 业务异常类型与错误文案
- Repository 与状态机的自动化测试范围

### 1.2 不在本范围

- 视觉 API 调用与解析（A）
- 页面模板、表单校验与按钮显隐（C）
- 工程骨架、数据源配置、全局异常处理、端到端测试（D）
- 文件存储路径与 URL 规则（D）

### 1.3 硬性约束

1. 业务规则只写在 `service` 层，SQL 只做增删改查。
2. 状态合法性由后端二次校验，不依赖页面按钮。
3. 状态变更与 `audit_event` 写入必须在同一个事务内。
4. 任何对外字段变更，先改本文档再改代码。

## 2. 状态模型

### 2.1 两个独立状态机

| 对象 | 状态 | 说明 |
|---|---|---|
| `Hazard` | `PENDING_CONFIRM` / `CONFIRMED` / `REJECTED` | 人工确认状态 |
| `WorkOrder` | `PENDING_REMEDIATION` / `PENDING_REVIEW` / `CLOSED` | 工单流转状态，创建即进入待整改 |

### 2.2 Hazard 转换矩阵

| 当前状态 | 动作（C 契约 R5 的 action） | 目标状态 | 前置条件 |
|---|---|---|---|
| `PENDING_CONFIRM` | `CONFIRM` | `CONFIRMED` | 无 |
| `PENDING_CONFIRM` | `EDIT` | `CONFIRMED` | 类别、位置、证据、建议措施、风险等级均必填 |
| `PENDING_CONFIRM` | `REJECT` | `REJECTED` | `reviewNote` 必填 |
| 其他 | 任意 | — | 拒绝，抛 `ILLEGAL_TRANSITION` |

### 2.3 WorkOrder 转换矩阵

| 当前状态 | 动作 | 目标状态 | 前置条件 |
|---|---|---|---|
| （无工单） | `CREATE` | `PENDING_REMEDIATION` | ① hazard 状态 = `CONFIRMED` ② 该 hazard 未建过工单 ③ `assignee` 非空 ④ `dueDate` 不早于今天 |
| `PENDING_REMEDIATION` | `SUBMIT_REMEDIATION` | `PENDING_REVIEW` | ① `remediationNote` 非空 ② `afterImagePath` 非空 |
| `PENDING_REVIEW` | `PASS` | `CLOSED` | `reviewComment` 可为空 |
| `PENDING_REVIEW` | `RETURN` | `PENDING_REMEDIATION` | `reviewComment` 必填 |
| `CLOSED` | 任意 | — | 拒绝，抛 `ILLEGAL_TRANSITION` |

### 2.4 并发与重复提交

所有状态更新使用**条件更新**，以受影响行数判定成功：

```sql
UPDATE work_order SET status = ? WHERE id = ? AND status = ?
```

受影响行数为 0 时抛业务异常。禁止"先查后改"两次独立操作。

### 2.5 历史保留

- `work_order.remediation_note` / `after_image_path` 只保留**最新一次**整改提交。
- 每次提交的整改说明与照片路径写入 `audit_event.detail`（JSON 文本），用于退回后追溯历史。
- 复查退回**不得清空**整改说明，以最新一次内容为准，历史由审计记录承载。

## 3. 数据表设计

完整 DDL 见 `_docs/B模块-schema.sql`，工程骨架合并到 main 后移动到 `src/main/resources/schema.sql`。

### 3.1 inspection（巡检记录）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `lab_name` | TEXT NOT NULL | 实验室名称 |
| `inspector` | TEXT NOT NULL | 巡检人 |
| `inspected_at` | TEXT NOT NULL | ISO-8601 巡检时间 |
| `image_path` | TEXT NOT NULL | 原图相对路径（由 D 的存储规则决定） |
| `analysis_source` | TEXT NOT NULL | `REAL` / `DEMO` |
| `analysis_status` | TEXT NOT NULL | `PENDING` / `SUCCESS` / `FAILED` |
| `ai_raw_response` | TEXT | AI 原始响应全文，失败时为错误摘要 |
| `note` | TEXT | 备注 |
| `created_at` | TEXT NOT NULL | ISO-8601 |

### 3.2 hazard（隐患）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `inspection_id` | INTEGER NOT NULL FK | 关联巡检 |
| `category` | TEXT NOT NULL | 隐患类别 |
| `risk_level` | TEXT NOT NULL | `HIGH` / `MEDIUM` / `LOW` |
| `risk_level_source` | TEXT NOT NULL | `RULE` / `MANUAL` |
| `location` | TEXT NOT NULL | 位置描述 |
| `evidence` | TEXT NOT NULL | 可见证据 |
| `confidence` | REAL | 可为空，空表示模型未提供 |
| `uncertainty` | TEXT | 不确定性说明 |
| `suggested_action` | TEXT NOT NULL | 建议整改措施 |
| `needs_human_review` | INTEGER NOT NULL | 0 / 1 |
| `status` | TEXT NOT NULL | `PENDING_CONFIRM` / `CONFIRMED` / `REJECTED` |
| `review_note` | TEXT | 驳回理由 |
| `confirmed_by` | TEXT | 确认人 |
| `confirmed_at` | TEXT | ISO-8601 |
| `created_at` | TEXT NOT NULL | ISO-8601 |

### 3.3 work_order（整改工单）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `hazard_id` | INTEGER NOT NULL **UNIQUE** FK | 同一隐患只允许一张工单 |
| `assignee` | TEXT NOT NULL | 责任人 |
| `due_date` | TEXT NOT NULL | ISO-8601 日期 |
| `status` | TEXT NOT NULL | 三状态 |
| `note` | TEXT | 创建备注 |
| `remediation_note` | TEXT | 最新一次整改说明 |
| `after_image_path` | TEXT | 最新一次整改照片 |
| `review_comment` | TEXT | 最新一次复查意见 |
| `reviewed_by` | TEXT | 复查人 |
| `reviewed_at` | TEXT | ISO-8601 |
| `created_at` | TEXT NOT NULL | ISO-8601 |
| `updated_at` | TEXT NOT NULL | ISO-8601 |

### 3.4 audit_event（操作审计）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `entity_type` | TEXT NOT NULL | `INSPECTION` / `HAZARD` / `WORK_ORDER` |
| `entity_id` | INTEGER NOT NULL | 对象主键 |
| `action` | TEXT NOT NULL | 见下方动作枚举 |
| `from_status` | TEXT | 变更前状态 |
| `to_status` | TEXT | 变更后状态 |
| `operator` | TEXT NOT NULL | 操作人 |
| `comment` | TEXT | 操作说明 |
| `detail` | TEXT | JSON 快照（整改说明、图片路径等） |
| `created_at` | TEXT NOT NULL | ISO-8601 |

动作枚举：`CREATE_INSPECTION`、`ANALYZE`、`CONFIRM_HAZARD`、`EDIT_HAZARD`、`REJECT_HAZARD`、`CREATE_WORK_ORDER`、`SUBMIT_REMEDIATION`、`PASS_REVIEW`、`RETURN_REMEDIATION`。

### 3.5 索引

```sql
CREATE INDEX idx_hazard_inspection ON hazard(inspection_id);
CREATE INDEX idx_work_order_status ON work_order(status);
CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id);
```

### 3.6 SQLite 映射约定

| 事项 | 约定 |
|---|---|
| 时间 | 一律 TEXT 存 ISO-8601（`LocalDateTime.toString()`），**不使用** `CURRENT_TIMESTAMP` 默认值，由代码显式写入 |
| 日期 | `LocalDate.toString()`，形如 `2026-10-21` |
| 布尔 | INTEGER 0/1 |
| 枚举 | 存 `name()` 字符串，禁止 ordinal |
| 主键 | `GeneratedKeyHolder` 取回，禁止 `SELECT last_insert_rowid()` 二次查询 |
| 外键 | 依赖启动时 `PRAGMA foreign_keys = ON`（由 D 在数据源初始化时配置） |
| 建表 | 全部 `CREATE TABLE IF NOT EXISTS`，支持重复启动 |

## 4. 对外接口

### 4.1 HazardService

```java
void confirm(long hazardId, String operator);
void edit(long hazardId, HazardEditCommand command, String operator);
void reject(long hazardId, String reviewNote, String operator);
Hazard get(long hazardId);
List<Hazard> listByInspection(long inspectionId);
List<AuditEvent> timeline(long hazardId);
```

### 4.2 WorkOrderService

```java
long create(long hazardId, String assignee, LocalDate dueDate, String note, String operator);
void submitRemediation(long workOrderId, String remediationNote, String afterImagePath, String operator);
void review(long workOrderId, boolean passed, String reviewComment, String operator);
WorkOrderDetail detail(long workOrderId);
List<WorkOrderDetail> list(WorkOrderStatus status);   // status 为 null 表示全部
List<AuditEvent> timeline(long workOrderId);
```

### 4.3 InspectionService

```java
long create(Inspection inspection);
List<Long> recordAnalysisResult(long inspectionId, AiResultSource source,
                                String rawResponse, List<Hazard> hazards);
void recordAnalysisFailure(long inspectionId, String rawResponse);
Inspection get(long inspectionId);
List<Inspection> list();
List<Hazard> listHazards(long inspectionId);
List<AuditEvent> timeline(long inspectionId);
```

只接收分析结果的字段（来源、原始响应、隐患列表），**不依赖 A 的结果类型**，由调用方从各自的分析结果中取值传入。

### 4.4 与 C 契约路由的对应

| C 契约路由 | 调用的 Service 方法 |
|---|---|
| R5 `POST /hazards/{id}/confirm` | `confirm` / `edit` / `reject`（按 action 分派） |
| R7 `GET /workorders/new` | `hazardService.get(id)` + `workOrderService.existsByHazard(id)` |
| R8 `POST /workorders` | `workOrderService.create(...)` |
| R10 `POST /workorders/{id}/remediation` | `workOrderService.submitRemediation(...)` |
| R11 `POST /workorders/{id}/review` | `workOrderService.review(...)` |
| R6 `GET /workorders` | `workOrderService.list(status)` |
| R9 `GET /workorders/{id}` | `workOrderService.detail(id)` |

### 4.5 视图对象

- `WorkOrderDetail`：工单 + 关联隐患 + 关联巡检（含原图路径、实验室名、整改前后照片、复查信息）
- 页面渲染所需字段全部由该视图提供，模板不额外查询数据库

## 5. 异常与错误文案

统一异常：`WorkflowException`，携带 `Code`。

| Code | 触发场景 | 页面文案（C 可直接展示 getMessage） |
|---|---|---|
| `HAZARD_NOT_FOUND` | 隐患不存在 | 隐患不存在或已被删除 |
| `HAZARD_NOT_CONFIRMED` | 未确认隐患建单 | 风险未确认，不能创建工单 |
| `HAZARD_ALREADY_HAS_ORDER` | 重复建单 | 该隐患已存在整改工单，不能重复创建 |
| `WORK_ORDER_NOT_FOUND` | 工单不存在 | 工单不存在或已被删除 |
| `ILLEGAL_TRANSITION` | 状态不允许 | 当前状态（{待整改/待复查/已关闭}）不允许该操作 |
| `INVALID_ARGUMENT` | 参数校验失败 | 具体字段提示 |

调用方式：由 D 的全局异常处理器捕获 `WorkflowException`，取 `getMessage()` 渲染到页面。

## 6. 与现有代码的合并规则

### 6.1 背景

`origin/A` 分支已存在一份骨架，其中 `schema.sql`、`Hazard.java`、`WorkOrder.java`、`WorkOrderService.java` 是**临时占位实现**，与 B 的职责重叠。

### 6.2 文件归属与冲突处理

| 文件 | A 分支现状 | B 分支处理 | 合并规则 |
|---|---|---|---|
| `src/main/resources/schema.sql` | 单表 `work_orders` | 暂存 `_docs/B模块-schema.sql`，骨架进 main 后移到 resources | **以 B 版本为准** |
| `model/Hazard.java` | 9 字段 record，无 id / 无确认状态 | 扩展为完整实体 | **以 B 版本为准** |
| `model/WorkOrder.java` | 9 字段 record，字段为 owner | 扩展为完整实体，owner 改名 assignee | **以 B 版本为准** |
| `model/AiResultSource.java` | REAL / DEMO | 内容保持一致，不修改 | 保留任一份 |
| `service/WorkOrderService.java` | 直接写 SQL 的占位实现 | 按本契约重写 | **以 B 版本为准** |
| `pom.xml` | 已含 web/thymeleaf/validation/jdbc/sqlite/test | **B 不新增 pom.xml**，等骨架合并后 rebase | 以 A 版本为准 |
| `application.properties` | 已有配置 | **B 不修改** | 以 D / A 版本为准 |
| `templates/`、`static/` | C 的领地 | **B 不碰** | 以 C 版本为准 |

### 6.3 对 A 的影响（需 A 确认）

1. `Hazard` 构造函数参数增加，`HomeController` 中 `new Hazard(...)` 需调整。
2. `WorkOrderService.create(Hazard, String)` 签名变更为 `create(long hazardId, String assignee, LocalDate dueDate, String note, String operator)`。
3. `WorkOrderService.findAll()` 由 `list(WorkOrderStatus)` 取代。
4. 若 A 的 `HomeController` 是临时脚手架、最终由 C 的 Controller 替换，则以上变更只需在合并时同步一次。

**B 分支不合并 A，也不修改上述任何非 B 文件**；以上影响由集成方在合并时统一处理。

## 7. 测试现状

| 测试类 | 覆盖内容 | 依赖 |
|---|---|---|
| `WorkOrderStateMachineTest` | 3×3 全部状态组合：4 条合法路径通过，5 条非法路径抛异常，转换表只读 | 纯逻辑，无 DB |
| `HazardStateMachineTest` | 隐患 3 状态合法/非法转换 | 纯逻辑，无 DB |
| `WorkflowPersistenceTest` | 四表完整链路、重复确认返回 0 行、整改+复查+审计、数据落到文件后换连接可读 | 文件型 SQLite |
| `WorkOrderServiceTest` | 未确认建单被拒、重复建单被拒、无照片提交被拒、复查退回无意见被拒、关闭后再提交被拒、三条审计顺序 | 文件型 SQLite + Spring 上下文 |
| `HazardServiceTest` | 确认写审计、重复确认被拒、驳回需理由、驳回后不可确认、修改后来源变 MANUAL | 文件型 SQLite + Spring 上下文 |

持久化测试使用文件型 `jdbc:sqlite:target/workflow-persistence.db`，**不使用内存库**，以验证“重启后数据保留”。

model 与 workflow 层已用 `javac` 编译验证通过；repository / service 层因本机无 Maven 及 Spring 依赖缓存，尚未实际执行 `mvn test`。

## 8. 待确认事项

| # | 事项 | 需谁确认 |
|---|---|---|
| 1 | 接受上述 4 个文件的合并规则（以 B 版本为准） | A / D |
| 2 | `inspection.analysis_source` 与 A 的 `AiResultSource` 是否统一为一个枚举 | A |
| 3 | 整改照片路径由 D 的存储规则生成，B 只存字符串 | D |
| 4 | 工单详情页是否展示审计时间线 | C |
| 5 | `PRAGMA foreign_keys = ON` 的配置位置 | D |

## 9. 交付清单（B 部分）

| 交付物 | 文件 |
|---|---|
| SQLite 四表 schema（草案，待集成时迁入 resources） | `_docs/B模块-schema.sql` |
| 领域模型 | `model/` 下 Hazard、Inspection、WorkOrder、WorkOrderDetail、AuditEvent 及 4 个枚举 |
| 状态机 | `workflow/` 下 HazardStateMachine、WorkOrderStateMachine、WorkflowAction、WorkflowException |
| 仓储层 | `repository/` 下 Inspection、Hazard、WorkOrder、AuditEvent 四个仓储与 RowValues |
| 业务层 | `service/` 下 InspectionService、HazardService、WorkOrderService、HazardEditCommand |
| 测试 | `workflow/HazardStateMachineTest`、`workflow/WorkOrderStateMachineTest`、`repository/WorkflowPersistenceTest` |
| 设计文档 | `_docs/B模块-设计契约.md` |

边界说明：以上文件均属于 B；`pom.xml`、`application.properties`、`resources/schema.sql`、`templates/`、`controller/` 由 A / C / D 负责，B 不做修改。
