-- v4.3.0 R8/数量规则：销售链路与 BOM 子件数量统一为正整数。
-- 历史小数已在 V103 中 ROUND 清理，这里改列类型并加约束。

-- 1) 销售订单行：qty 必须为正整数
UPDATE order_lines SET qty = ROUND(qty) WHERE qty IS NOT NULL AND qty <> ROUND(qty);
ALTER TABLE order_lines ALTER COLUMN qty TYPE INTEGER USING ROUND(qty)::INTEGER;
ALTER TABLE order_lines ALTER COLUMN qty SET NOT NULL;
ALTER TABLE order_lines DROP CONSTRAINT IF EXISTS chk_order_lines_qty_pos;
ALTER TABLE order_lines ADD CONSTRAINT chk_order_lines_qty_pos CHECK (qty > 0);

-- 订单行结案数量同步为整数
UPDATE order_lines SET closed_qty = ROUND(closed_qty) WHERE closed_qty IS NOT NULL AND closed_qty <> ROUND(closed_qty);
ALTER TABLE order_lines ALTER COLUMN closed_qty TYPE INTEGER USING ROUND(closed_qty)::INTEGER;
ALTER TABLE order_lines ALTER COLUMN closed_qty SET DEFAULT 0;

-- 组件用量（母件1套对应子件数量，BOM 展开后均为整数）
UPDATE order_lines SET component_qty = ROUND(component_qty) WHERE component_qty IS NOT NULL AND component_qty <> ROUND(component_qty);
ALTER TABLE order_lines ALTER COLUMN component_qty TYPE INTEGER USING ROUND(component_qty)::INTEGER;

-- 2) 销售出库行
UPDATE sales_out_lines SET qty = ROUND(qty) WHERE qty IS NOT NULL AND qty <> ROUND(qty);
UPDATE sales_out_lines SET shipped_qty = ROUND(shipped_qty) WHERE shipped_qty IS NOT NULL AND shipped_qty <> ROUND(shipped_qty);
UPDATE sales_out_lines SET expected_qty = ROUND(expected_qty) WHERE expected_qty IS NOT NULL AND expected_qty <> ROUND(expected_qty);
UPDATE sales_out_lines SET return_locked_qty = ROUND(return_locked_qty) WHERE return_locked_qty IS NOT NULL AND return_locked_qty <> ROUND(return_locked_qty);
UPDATE sales_out_lines SET returned_qty = ROUND(returned_qty) WHERE returned_qty IS NOT NULL AND returned_qty <> ROUND(returned_qty);
UPDATE sales_out_lines SET component_qty = ROUND(component_qty) WHERE component_qty IS NOT NULL AND component_qty <> ROUND(component_qty);
ALTER TABLE sales_out_lines ALTER COLUMN qty TYPE INTEGER USING ROUND(qty)::INTEGER;
ALTER TABLE sales_out_lines ALTER COLUMN shipped_qty TYPE INTEGER USING ROUND(shipped_qty)::INTEGER;
ALTER TABLE sales_out_lines ALTER COLUMN expected_qty TYPE INTEGER USING ROUND(expected_qty)::INTEGER;
ALTER TABLE sales_out_lines ALTER COLUMN return_locked_qty TYPE INTEGER USING ROUND(return_locked_qty)::INTEGER;
ALTER TABLE sales_out_lines ALTER COLUMN returned_qty TYPE INTEGER USING ROUND(returned_qty)::INTEGER;
ALTER TABLE sales_out_lines ALTER COLUMN component_qty TYPE INTEGER USING ROUND(component_qty)::INTEGER;

-- 3) BOM 子件用量必须为正整数
UPDATE product_bundle_lines SET quantity = ROUND(quantity) WHERE quantity IS NOT NULL AND quantity <> ROUND(quantity);
ALTER TABLE product_bundle_lines ALTER COLUMN quantity TYPE INTEGER USING ROUND(quantity)::INTEGER;
ALTER TABLE product_bundle_lines ALTER COLUMN quantity SET NOT NULL;
ALTER TABLE product_bundle_lines DROP CONSTRAINT IF EXISTS chk_pbl_qty_pos;
ALTER TABLE product_bundle_lines ADD CONSTRAINT chk_pbl_qty_pos CHECK (quantity > 0);
COMMENT ON COLUMN product_bundle_lines.quantity IS 'v4.3.0：子件用量，必须为正整数';
