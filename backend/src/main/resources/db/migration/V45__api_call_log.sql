-- v3.8.2 接口调用日志：记录外部调用 DMS 的入站请求，以及 DMS 调用外部系统的出站请求
CREATE TABLE IF NOT EXISTS api_call_log (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID,
    direction     VARCHAR(8)  NOT NULL,           -- IN=外部调用DMS, OUT=DMS调用外部
    request_id    VARCHAR(64),                    -- 入站请求链路ID
    trace_id      VARCHAR(64),
    system        VARCHAR(32),                    -- 外部系统标识(ERP/WMS/HR/UDI/CA/第三方)
    endpoint      VARCHAR(32),                    -- 业务端点编码(可扩展)
    http_method   VARCHAR(8),
    url           VARCHAR(1024),
    path          VARCHAR(512),
    status_code   INTEGER,                        -- HTTP 状态码
    biz_code      INTEGER,                        -- 业务 code(ApiResponse.code)
    success       BOOLEAN,
    client_ip     VARCHAR(64),
    user_id       BIGINT,
    username      VARCHAR(64),
    app_key       VARCHAR(64),                    -- 未来第三方应用接入的应用标识
    request_headers TEXT,
    request_body  TEXT,
    response_body TEXT,
    error_msg     TEXT,
    spent_ms      BIGINT,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_call_log_started   ON api_call_log (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_call_log_direction ON api_call_log (direction, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_call_log_system    ON api_call_log (system, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_call_log_tenant    ON api_call_log (tenant_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_call_log_req       ON api_call_log (request_id);
COMMENT ON TABLE api_call_log IS '接口调用日志(入站+出站)';
