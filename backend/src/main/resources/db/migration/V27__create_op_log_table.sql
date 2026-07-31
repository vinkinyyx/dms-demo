-- V27: 全链路操作日志表 (operation-log-fullchain spec)
CREATE TABLE IF NOT EXISTS op_log (
    id           BIGSERIAL PRIMARY KEY,
    request_id   VARCHAR(64),
    trace_id     VARCHAR(64),
    tenant_id    UUID,
    user_id      BIGINT,
    username     VARCHAR(64),
    layer        VARCHAR(16)         NOT NULL,
    method       VARCHAR(255),
    http_method  VARCHAR(8),
    path         VARCHAR(255),
    status       INTEGER,
    spent_ms     BIGINT,
    ip           VARCHAR(64),
    user_agent   VARCHAR(255),
    request_body TEXT,
    response     TEXT,
    stack        TEXT,
    biz_type     VARCHAR(32),
    biz_id       VARCHAR(64),
    action       VARCHAR(16),
    remark       VARCHAR(255),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS op_log_created_at_idx ON op_log (created_at DESC);
CREATE INDEX IF NOT EXISTS op_log_user_idx      ON op_log (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS op_log_layer_idx     ON op_log (layer, created_at DESC);
CREATE INDEX IF NOT EXISTS op_log_path_idx      ON op_log (path, created_at DESC);
