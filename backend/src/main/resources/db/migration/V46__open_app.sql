-- v3.8.3 对外接口：第三方应用接入凭据（HMAC 签名鉴权）
CREATE TABLE IF NOT EXISTS open_app (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    app_key       VARCHAR(64) NOT NULL,
    app_secret    VARCHAR(128) NOT NULL,
    app_name      VARCHAR(128) NOT NULL,
    system        VARCHAR(32),                -- ERP/WMS/HR/UDI/CA/第三方
    status        VARCHAR(16) NOT NULL DEFAULT 'active',  -- active/disabled
    allowed_ips   VARCHAR(512),               -- 可选 IP 白名单，逗号分隔
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_open_app_key UNIQUE (app_key)
);
CREATE INDEX IF NOT EXISTS idx_open_app_tenant ON open_app (tenant_id);

-- 默认接入应用（测试用）。app_secret 仅为示例，生产请通过后台重置。
INSERT INTO open_app (tenant_id, app_key, app_secret, app_name, system, status)
SELECT t.id, 'dms-demo-app', '8c39b1f7e2a44d6b9f0a1c2d3e4f5a6b', 'DMS演示对接应用', 'DEMO', 'active'
FROM tenants t WHERE t.code = 'default'
ON CONFLICT (app_key) DO NOTHING;

COMMENT ON TABLE open_app IS '对外接口应用凭据(HMAC)';
