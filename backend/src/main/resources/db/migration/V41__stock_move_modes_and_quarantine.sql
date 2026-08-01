-- V41: 库存移动支持两种模式 + 新增"隔离"库存状态
--   move_type: STATUS_ADJUST(仓内状态调整) / WAREHOUSE_TRANSFER(跨仓移动)
--   库存状态新增 QUARANTINED(隔离)，保留 QUALIFIED(合格)/PENDING(待检)/DEFECTIVE(不合格)

ALTER TABLE stock_moves
  ADD COLUMN IF NOT EXISTS move_type VARCHAR(24),
  ADD COLUMN IF NOT EXISTS from_stock_status VARCHAR(24),
  ADD COLUMN IF NOT EXISTS to_stock_status VARCHAR(24);

-- 历史库中 move_type 可能为 VARCHAR(16)，WAREHOUSE_TRANSFER(17字符) 超长，统一扩到 24
ALTER TABLE stock_moves ALTER COLUMN move_type TYPE VARCHAR(24);

ALTER TABLE stock_move_lines
  ADD COLUMN IF NOT EXISTS src_inventory_id BIGINT,
  ADD COLUMN IF NOT EXISTS from_stock_status VARCHAR(24),
  ADD COLUMN IF NOT EXISTS to_stock_status VARCHAR(24),
  ADD COLUMN IF NOT EXISTS stock_batch_id BIGINT;

-- 兼容历史数据：旧跨仓移动视为 WAREHOUSE_TRANSFER（含历史值 WAREHOUSE）
UPDATE stock_moves SET move_type = 'WAREHOUSE_TRANSFER' WHERE move_type IS NULL OR move_type = 'WAREHOUSE' OR move_type NOT IN ('STATUS_ADJUST','WAREHOUSE_TRANSFER');

-- 字典：新增"隔离"状态
INSERT INTO dict_types(tenant_id, code, name, description)
SELECT t.id, 'stock_status', '库存状态', '合格/不合格/隔离/待检'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM dict_types dt WHERE dt.tenant_id = t.id AND dt.code = 'stock_status');

INSERT INTO dict_items(type_id, code, name, seq, status)
SELECT dt.id, 'QUARANTINED', '隔离', 4, 'active'
FROM dict_types dt
WHERE dt.code = 'stock_status'
  AND NOT EXISTS (SELECT 1 FROM dict_items di WHERE di.type_id = dt.id AND di.code = 'QUARANTINED');
