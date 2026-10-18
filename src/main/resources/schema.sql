CREATE TABLE IF NOT EXISTS work_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    risk_level TEXT NOT NULL,
    location TEXT NOT NULL,
    evidence TEXT NOT NULL,
    suggested_action TEXT NOT NULL,
    owner TEXT NOT NULL DEFAULT '待分配',
    status TEXT NOT NULL CHECK (status IN ('PENDING_REMEDIATION', 'PENDING_REVIEW', 'CLOSED')),
    rectification_note TEXT NOT NULL DEFAULT '',
    review_note TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
