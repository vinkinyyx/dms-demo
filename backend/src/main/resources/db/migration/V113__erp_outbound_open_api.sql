-- R9 ERP -> DMS 销售出库回传标准对外接口
-- 1) erp_outbound_callbacks 增加 request_id 用于日志追溯，failed_lines 记录逐行失败原因
ALTER TABLE erp_outbound_callbacks ADD COLUMN IF NOT EXISTS request_id   VARCHAR(128);
ALTER TABLE erp_outbound_callbacks ADD COLUMN IF NOT EXISTS failed_lines JSONB;

COMMENT ON COLUMN erp_outbound_callbacks.request_id   IS 'ERP 请求流水号，仅用于日志追溯';
COMMENT ON COLUMN erp_outbound_callbacks.failed_lines IS '逐行校验失败明细 [{lineNo,product,reason}]';

CREATE INDEX IF NOT EXISTS idx_erp_outbound_callback_request
    ON erp_outbound_callbacks (request_id);

-- 2) 为 default 租户确保一个 system=ERP、status=active 的 open_app（不影响已有的 dms-demo-app）
INSERT INTO open_app (tenant_id, app_key, app_secret, app_name, system, status)
SELECT t.id,
       'dms-erp-app',
       '0a1b2c3d4e5f60718293a4b5c6d7e8f9',
       'ERP标准对接应用',
       'ERP',
       'active'
FROM tenants t
WHERE t.code = 'default'
ON CONFLICT (app_key) DO NOTHING;