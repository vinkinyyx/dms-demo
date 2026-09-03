-- ---------------------------------------------------------------------
-- V147: 授权-下单挂钩开关 + 授权产品线维度与排他/终止/续约
-- 1) system_settings 增加租户级开关 order.authz.enforce（默认 false=不强制）
--    scope='tenant'，按租户插入默认行；缺失时 Service 回退 false（可直接下单）。
-- 2) authorizations 增加排他检查辅助索引（时间段重叠判定在 Service 内完成，
--    CSV 产品线/终端无法建简单唯一约束，故以 Service 预检 + 状态过滤为准）。
-- 注：authorizations.product_lines / terminal_ids / status 字段已存在，
--    新流程复用 product_lines(产品线 CSV)、terminal_ids(医院 CSV)、status 新增
--    状态值 not_started/terminate_pending；其中 terminate_pending(17字符) 超出原
--    VARCHAR(16)，故第 4 步将 status 列扩容为 VARCHAR(32)。
-- ---------------------------------------------------------------------

-- 1. 授权-下单强制开关：为每个租户插入默认 false（幂等）
INSERT INTO system_settings (scope, tenant_id, key, value_json, description)
SELECT 'tenant', t.id, 'order.authz.enforce', CAST('false' AS jsonb),
       '授权与下单挂钩开关：true=无有效授权禁止下单/出库；false=授权与下单解耦，可直接下单'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings s
    WHERE s.scope = 'tenant' AND s.tenant_id = t.id AND s.key = 'order.authz.enforce'
);

-- 2. 授权排他/有效期查询辅助索引（幂等）
CREATE INDEX IF NOT EXISTS idx_authorizations_exclusive
    ON authorizations (tenant_id, dealer_id, status, valid_from, valid_to)
    WHERE deleted_at IS NULL;

-- 3. 授权产品线列表查询索引（product_lines 为 CSV，仍以全表条件过滤为主，索引辅助经销商+状态）
CREATE INDEX IF NOT EXISTS idx_authorizations_dealer_status
    ON authorizations (tenant_id, status, valid_from, valid_to)
    WHERE deleted_at IS NULL;

-- 4. 授权状态列扩容：新增 terminate_pending(17字符) 超出原 VARCHAR(16)
ALTER TABLE authorizations ALTER COLUMN status TYPE VARCHAR(32);
