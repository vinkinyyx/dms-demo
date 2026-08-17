-- V88: 报表订阅（DAT-01/05）
CREATE TABLE IF NOT EXISTS report_subscription (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    name          VARCHAR(128) NOT NULL,
    report_type   VARCHAR(64)  NOT NULL,
    params        TEXT,                       -- JSON 查询参数
    cron_expr     VARCHAR(64)  NOT NULL,      -- DAILY/WEEKLY/MONTHLY
    emails        TEXT,                       -- JSON 数组，额外收件人
    active        BOOLEAN DEFAULT TRUE,
    last_run_at   TIMESTAMPTZ,
    last_status   VARCHAR(16),
    last_error    TEXT,
    created_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_report_sub_tenant ON report_subscription (tenant_id, active);