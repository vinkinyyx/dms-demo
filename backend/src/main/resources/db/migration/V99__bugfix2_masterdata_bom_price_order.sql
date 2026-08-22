-- v4.0.0-bugfix.2 迁移
-- 1) 订单行增加母子件层级字段
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS line_level VARCHAR(16);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS bom_parent_line_id BIGINT;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS is_group_header BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_order_lines_parent_line ON order_lines(bom_parent_line_id);

-- 2) 产品价格：补充 BOM 母件不可建价在代码层校验；新增版本状态注释
COMMENT ON COLUMN product_prices.status IS 'active=生效, inactive=失效; bugfix.2起价格只增不改';

-- 3) 修复测试数据：销售出库数量、订单行数量为小数的改为整数（以发货单号 GI-20260816-00009 为例）
UPDATE sales_out_lines SET shipped_qty = CEIL(COALESCE(shipped_qty,qty)) WHERE COALESCE(shipped_qty,qty) <> ROUND(COALESCE(shipped_qty,qty));
UPDATE sales_out_lines SET qty = ROUND(qty) WHERE qty IS NOT NULL AND qty <> ROUND(qty);
UPDATE order_lines SET qty = ROUND(qty) WHERE qty IS NOT NULL AND qty <> ROUND(qty);

-- 4) 产品表废弃单价/税率字段（列保留兼容历史，API不再暴露）
COMMENT ON COLUMN products.current_price IS 'DEPRECATED v4.0.0-bugfix.2: 价格统一由 product_prices 维护';
COMMENT ON COLUMN products.tax_rate IS 'DEPRECATED v4.0.0-bugfix.2: 价格统一由 product_prices 维护';
