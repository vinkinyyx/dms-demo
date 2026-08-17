-- V86: 异步导入导出任务表（BIZ-07 异步导入导出基础版）
CREATE TABLE IF NOT EXISTS async_task (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID,
    task_type     VARCHAR(32)  NOT NULL,   -- IMPORT / EXPORT / REPORT
    biz_type      VARCHAR(64),             -- contracts / sales_orders / products ...
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/SUCCESS/FAILED
    file_name     VARCHAR(255),
    object_key    VARCHAR(512),
    total_rows    INTEGER DEFAULT 0,
    success_rows  INTEGER DEFAULT 0,
    failed_rows   INTEGER DEFAULT 0,
    error_message TEXT,
    params        TEXT,                    -- JSON: 查询参数快照
    created_by    BIGINT,
    created_name  VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS async_task_tenant_idx ON async_task (tenant_id, id DESC);
CREATE INDEX IF NOT EXISTS async_task_status_idx ON async_task (status);
CREATE INDEX IF NOT EXISTS async_task_type_idx   ON async_task (task_type, id DESC);