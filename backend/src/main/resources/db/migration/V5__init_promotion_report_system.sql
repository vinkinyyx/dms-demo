-- =====================================================================
-- V5: 促销 / 报表画像 / 通用系统
-- 说明：
--   J. 促销：promotions（含 CHECK 约束 promo_type IN
--       ('MOQ','FULL_REDUCTION','GIFT','BUNDLE')；API 层 V1 仅允许
--       MOQ/FULL_REDUCTION）, promotion_rules, promotion_status_logs
--   K. 报表画像：rebate_previews, rebate_settlements, dealer_kpi_snapshots
--   L. 通用/审批/系统：audit_logs（按月 RANGE 分区）,
--       dict_types, dict_items, system_settings, async_jobs,
--       notifications, approval_tasks, approval_history
-- 分区：audit_logs 2026-07~2026-12 六个月分区
-- 兼容：Flyway 8.x + PostgreSQL 14
-- =====================================================================

-- ---------------------------------------------------------------------
-- J. 促销
-- ---------------------------------------------------------------------
CREATE TABLE promotions (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    code           VARCHAR(64) UNIQUE,
    name           VARCHAR(200) NOT NULL,
    promo_type     VARCHAR(16) NOT NULL,   -- MOQ / FULL_REDUCTION（V1）；GIFT / BUNDLE 保留
    priority       INT DEFAULT 50,
    valid_from     TIMESTAMPTZ,
    valid_to       TIMESTAMPTZ,
    dealer_scope   JSONB,
    product_scope  JSONB,
    exclusive      BOOLEAN DEFAULT false,
    status         VARCHAR(16) DEFAULT 'draft',
    description    TEXT,
    created_by     BIGINT,
    approved_by    BIGINT,
    approved_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    updated_by     BIGINT,
    version        INT DEFAULT 0,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT ck_promo_type_v1
        CHECK (promo_type IN ('MOQ','FULL_REDUCTION','GIFT','BUNDLE'))
);
CREATE INDEX idx_promo_active ON promotions(tenant_id, status, valid_from, valid_to);

CREATE TABLE promotion_rules (
    id            BIGSERIAL PRIMARY KEY,
    promotion_id  BIGINT REFERENCES promotions(id) ON DELETE CASCADE,
    seq           INT,
    rule_detail   JSONB NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_promo_rules_promo ON promotion_rules(promotion_id, seq);

CREATE TABLE promotion_status_logs (
    id            BIGSERIAL PRIMARY KEY,
    promotion_id  BIGINT REFERENCES promotions(id) ON DELETE CASCADE,
    from_status   VARCHAR(16),
    to_status     VARCHAR(16),
    operator_id   BIGINT,
    comment       TEXT,
    at_time       TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_promo_log_promo ON promotion_status_logs(promotion_id, at_time DESC);

-- ---------------------------------------------------------------------
-- K. 报表画像
-- ---------------------------------------------------------------------
CREATE TABLE rebate_previews (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    dealer_id          BIGINT,
    period_yyyymm      CHAR(6),
    target_amount      NUMERIC(18,2),
    actual_amount      NUMERIC(18,2),
    achievement_rate   NUMERIC(9,4),
    tier_hit           JSONB,
    gross_rebate       NUMERIC(18,2),
    deductions         JSONB,
    net_rebate         NUMERIC(18,2),
    snapshot_at        TIMESTAMPTZ DEFAULT now(),
    created_at         TIMESTAMPTZ DEFAULT now(),
    updated_at         TIMESTAMPTZ DEFAULT now(),
    version            INT DEFAULT 0,
    UNIQUE (tenant_id, dealer_id, period_yyyymm)
);

CREATE TABLE rebate_settlements (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    dealer_id       BIGINT,
    period_yyyymm   CHAR(6),
    net_rebate      NUMERIC(18,2),
    status          VARCHAR(16) DEFAULT 'LOCKED',
    settled_at      TIMESTAMPTZ DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    version         INT DEFAULT 0,
    UNIQUE (tenant_id, dealer_id, period_yyyymm)
);

CREATE TABLE dealer_kpi_snapshots (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            UUID,
    dealer_id            BIGINT,
    period_yyyymm        CHAR(6),
    stock_report_rate    NUMERIC(5,4),
    sales_report_rate    NUMERIC(5,4),
    order_pass_rate      NUMERIC(5,4),
    return_rate          NUMERIC(5,4),
    created_at           TIMESTAMPTZ DEFAULT now(),
    updated_at           TIMESTAMPTZ DEFAULT now(),
    UNIQUE (tenant_id, dealer_id, period_yyyymm)
);

-- ---------------------------------------------------------------------
-- L-1. audit_logs（按月 RANGE 分区）
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id             BIGSERIAL,
    tenant_id      UUID,
    user_id        BIGINT,
    action         VARCHAR(32),
    resource_type  VARCHAR(64),
    resource_id    VARCHAR(64),
    before         JSONB,
    after          JSONB,
    ip             VARCHAR(64),
    user_agent     TEXT,
    at_time        TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (id, at_time)
) PARTITION BY RANGE (at_time);

CREATE INDEX idx_audit_tenant_time ON audit_logs(tenant_id, at_time DESC);
CREATE INDEX idx_audit_user_time ON audit_logs(user_id, at_time DESC);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);

-- 2026-07 ~ 2026-12 六个月分区
CREATE TABLE audit_logs_2026_07 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');
CREATE TABLE audit_logs_2026_08 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');
CREATE TABLE audit_logs_2026_09 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');
CREATE TABLE audit_logs_2026_10 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');
CREATE TABLE audit_logs_2026_11 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
CREATE TABLE audit_logs_2026_12 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

-- ---------------------------------------------------------------------
-- L-2. 字典 / 系统参数 / 异步任务 / 通知
-- ---------------------------------------------------------------------
CREATE TABLE dict_types (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(200),
    description TEXT,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE TABLE dict_items (
    id         BIGSERIAL PRIMARY KEY,
    type_id    BIGINT REFERENCES dict_types(id) ON DELETE CASCADE,
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    seq        INT,
    status     VARCHAR(16) DEFAULT 'active',
    attrs      JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (type_id, code)
);

CREATE TABLE system_settings (
    id          BIGSERIAL PRIMARY KEY,
    scope       VARCHAR(16) NOT NULL,   -- global / tenant
    tenant_id   UUID,
    key         VARCHAR(128) NOT NULL,
    value_json  JSONB NOT NULL,
    description TEXT,
    updated_by  BIGINT,
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE (scope, tenant_id, key)
);

CREATE TABLE async_jobs (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID,
    user_id      BIGINT,
    job_type     VARCHAR(32),
    payload      JSONB,
    status       VARCHAR(16) DEFAULT 'PENDING',
    progress     INT DEFAULT 0,
    result       JSONB,
    error        TEXT,
    started_at   TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_async_jobs_user ON async_jobs(user_id, created_at DESC);
CREATE INDEX idx_async_jobs_status ON async_jobs(status);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID,
    user_id     BIGINT NOT NULL,
    channel     VARCHAR(16),   -- V1: INAPP / WECHAT_BOT / FEISHU_BOT
    title       VARCHAR(200),
    body        TEXT,
    ref_type    VARCHAR(32),
    ref_id      VARCHAR(64),
    is_read     BOOLEAN DEFAULT false,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_noti_user_unread ON notifications(user_id, is_read);

CREATE TABLE approval_tasks (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID,
    workflow_id         BIGINT,
    workflow_node_code  VARCHAR(64),
    ref_type            VARCHAR(32) NOT NULL,
    ref_id              BIGINT NOT NULL,
    assignee_id         BIGINT NOT NULL,
    status              VARCHAR(16) DEFAULT 'PENDING',
    action              VARCHAR(16),
    comment             TEXT,
    deadline            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ DEFAULT now(),
    done_at             TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ DEFAULT now(),
    version             INT DEFAULT 0
);
CREATE INDEX idx_appr_assignee ON approval_tasks(assignee_id, status);
CREATE INDEX idx_appr_ref ON approval_tasks(ref_type, ref_id);

CREATE TABLE approval_history (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT REFERENCES approval_tasks(id) ON DELETE CASCADE,
    ref_type     VARCHAR(32),
    ref_id       BIGINT,
    operator_id  BIGINT,
    action       VARCHAR(16),
    comment      TEXT,
    at_time      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_appr_hist_ref ON approval_history(ref_type, ref_id, at_time DESC);
