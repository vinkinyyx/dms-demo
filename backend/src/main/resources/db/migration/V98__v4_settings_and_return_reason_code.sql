-- v4.0.0: 销退原因编码 + 租户级 v4 设置表（库存模式/价格策略）
-- 1) orders 增加 reason_code（销退原因下拉编码），原 return_reason 保留为文本说明
ALTER TABLE orders ADD COLUMN IF NOT EXISTS reason_code VARCHAR(50);

-- 已有的销退单从 return_reason 文本反推编码（能匹配到枚举则回填）
UPDATE orders SET reason_code = 'NORMAL'
 WHERE reason_code IS NULL AND COALESCE(is_red, true) = true
   AND return_reason IS NOT NULL
   AND return_reason NOT IN ('PRE_OP_CONTAMINATION','QUALITY_ISSUE','NEAR_EXPIRY','EXPIRED','OVER_SHIP','CUSTOMER_RETURN','DAMAGED','OTHER');

-- 2) v4_settings：每个租户一行，控制库存模式/价格策略/促销开关
CREATE TABLE IF NOT EXISTS v4_settings (
  id BIGSERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL,
  inventory_mode VARCHAR(32) NOT NULL DEFAULT 'INVENTORY_VS_ERP',
  pricing_scope VARCHAR(32) NOT NULL DEFAULT 'DEALER',
  promotion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  bom_version_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  erp_callback_token VARCHAR(200),
  extra JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT uq_v4_settings_tenant UNIQUE (tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_v4_settings_tenant ON v4_settings(tenant_id);

-- 为已存在的租户初始化默认设置
INSERT INTO v4_settings (tenant_id, inventory_mode, pricing_scope, promotion_enabled, bom_version_enabled)
SELECT t.id, 'INVENTORY_VS_ERP', 'DEALER', TRUE, TRUE
  FROM tenants t
  LEFT JOIN v4_settings s ON s.tenant_id = t.id
 WHERE s.id IS NULL;
