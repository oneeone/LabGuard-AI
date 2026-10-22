-- =====================================================================
-- LabGuard AI — SQLite Schema（终版，成员 B 维护，集成合并）
-- 约定：全部 IF NOT EXISTS，支持重复启动；时间一律 TEXT ISO-8601，由代码写入。
-- risk_level 允许 UNKNOWN：AI 输出“待评估”等级时落库为该值，由人工确认时调整。
-- =====================================================================

-- 1. 巡检记录
CREATE TABLE IF NOT EXISTS inspection (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    lab_name        TEXT    NOT NULL,
    inspector       TEXT    NOT NULL,
    inspected_at    TEXT    NOT NULL,
    image_path      TEXT    NOT NULL,
    analysis_source TEXT    NOT NULL CHECK (analysis_source IN ('REAL', 'DEMO')),
    analysis_status TEXT    NOT NULL CHECK (analysis_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    ai_raw_response TEXT,
    note            TEXT,
    created_at      TEXT    NOT NULL
);

-- 2. 隐患
CREATE TABLE IF NOT EXISTS hazard (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    inspection_id      INTEGER NOT NULL,
    category           TEXT    NOT NULL,
    risk_level         TEXT    NOT NULL CHECK (risk_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN')),
    risk_level_source  TEXT    NOT NULL DEFAULT 'RULE' CHECK (risk_level_source IN ('RULE', 'MANUAL')),
    location           TEXT    NOT NULL,
    evidence           TEXT    NOT NULL,
    confidence         REAL,
    uncertainty        TEXT,
    suggested_action   TEXT    NOT NULL,
    needs_human_review INTEGER NOT NULL DEFAULT 1 CHECK (needs_human_review IN (0, 1)),
    status             TEXT    NOT NULL DEFAULT 'PENDING_CONFIRM'
                               CHECK (status IN ('PENDING_CONFIRM', 'CONFIRMED', 'REJECTED')),
    review_note        TEXT,
    confirmed_by       TEXT,
    confirmed_at       TEXT,
    created_at         TEXT    NOT NULL,
    FOREIGN KEY (inspection_id) REFERENCES inspection (id)
);

-- 3. 整改工单（同一隐患只允许一张工单）
CREATE TABLE IF NOT EXISTS work_order (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    hazard_id         INTEGER NOT NULL UNIQUE,
    assignee          TEXT    NOT NULL,
    due_date          TEXT    NOT NULL,
    status            TEXT    NOT NULL CHECK (status IN ('PENDING_REMEDIATION', 'PENDING_REVIEW', 'CLOSED')),
    note              TEXT,
    remediation_note  TEXT,
    after_image_path  TEXT,
    review_comment    TEXT,
    reviewed_by       TEXT,
    reviewed_at       TEXT,
    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL,
    FOREIGN KEY (hazard_id) REFERENCES hazard (id)
);

-- 4. 操作审计
CREATE TABLE IF NOT EXISTS audit_event (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type TEXT    NOT NULL CHECK (entity_type IN ('INSPECTION', 'HAZARD', 'WORK_ORDER')),
    entity_id   INTEGER NOT NULL,
    action      TEXT    NOT NULL,
    from_status TEXT,
    to_status   TEXT,
    operator    TEXT    NOT NULL,
    comment     TEXT,
    detail      TEXT,
    created_at  TEXT    NOT NULL
);

-- 5. AI 整改核验记录（只记录建议，不改工单状态）
CREATE TABLE IF NOT EXISTS ai_review (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    work_order_id    INTEGER NOT NULL,
    outcome          TEXT    NOT NULL CHECK (outcome IN ('PASSED', 'FAILED', 'INCONCLUSIVE')),
    evidence         TEXT    NOT NULL,
    checks_json      TEXT    NOT NULL,
    comparability    TEXT    NOT NULL CHECK (comparability IN ('COMPARABLE', 'PARTIAL', 'NOT_COMPARABLE')),
    uncertainty      TEXT,
    suggested_action TEXT,
    source           TEXT    NOT NULL CHECK (source IN ('REAL', 'DEMO')),
    model            TEXT,
    raw_response     TEXT,
    created_at       TEXT    NOT NULL,
    FOREIGN KEY (work_order_id) REFERENCES work_order (id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_hazard_inspection ON hazard (inspection_id);
CREATE INDEX IF NOT EXISTS idx_work_order_status ON work_order (status);
CREATE INDEX IF NOT EXISTS idx_audit_entity      ON audit_event (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_ai_review_order   ON ai_review (work_order_id);
