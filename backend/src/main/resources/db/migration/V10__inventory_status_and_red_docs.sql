-- ============================================================
-- V10: 库存状态机 + 授权改产品分类 + 销退/采退拆分 + 红字入出库
-- 需求:
--   1. 授权改用产品分类 (category_ids) 代替 product_lines
--   2. inventory 加 stock_status 列 (QUALIFIED/PENDING/DEFECTIVE)
--   3. orders 增加 is_red + ref_order_id 支持销退单
--   4. purchase_orders 同样支持采退单 (is_red + ref_po_id)
--   5. sales_outs 已有 is_red，收货入库时默认设为 PENDING
--   6. 为现有 inventory 数据随机分配库存状态
-- ============================================================

-- 1. inventory 加库存状态列
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='inventory' AND column_name='stock_status') THEN
        ALTER TABLE inventory ADD COLUMN stock_status VARCHAR(32) DEFAULT 'QUALIFIED';
        CREATE INDEX idx_inv_stock_status ON inventory(tenant_id, product_id, stock_status);
    END IF;
END $$;

-- 2. 授权表加 category_ids
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='authorizations' AND column_name='category_ids') THEN
        ALTER TABLE authorizations ADD COLUMN category_ids VARCHAR(500);
    END IF;
END $$;

-- 3. orders 加销退支持
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='orders' AND column_name='is_red') THEN
        ALTER TABLE orders ADD COLUMN is_red BOOLEAN DEFAULT false;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='orders' AND column_name='ref_order_id') THEN
        ALTER TABLE orders ADD COLUMN ref_order_id BIGINT;
    END IF;
END $$;

-- 4. purchase_orders 加采退支持
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='purchase_orders' AND column_name='is_red') THEN
        ALTER TABLE purchase_orders ADD COLUMN is_red BOOLEAN DEFAULT false;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='purchase_orders' AND column_name='ref_po_id') THEN
        ALTER TABLE purchase_orders ADD COLUMN ref_po_id BIGINT;
    END IF;
END $$;

-- 5. receipts 加红字支持（红字采购入库）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='receipts' AND column_name='is_red') THEN
        ALTER TABLE receipts ADD COLUMN is_red BOOLEAN DEFAULT false;
    END IF;
END $$;

-- 6. stock_moves 增加库存状态字段（表示状态间移动）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='stock_moves' AND column_name='src_status') THEN
        ALTER TABLE stock_moves ADD COLUMN src_status VARCHAR(32);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='stock_moves' AND column_name='dst_status') THEN
        ALTER TABLE stock_moves ADD COLUMN dst_status VARCHAR(32);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='stock_moves' AND column_name='move_type') THEN
        ALTER TABLE stock_moves ADD COLUMN move_type VARCHAR(16) DEFAULT 'WAREHOUSE';
    END IF;
END $$;

-- 7. 库存状态字典（3 种）
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES ('11111111-1111-1111-1111-111111111111', 'INVENTORY_STATUS', '库存状态', '合格/待检/不合格')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT dt.id, x.code, x.name, x.seq, 'active'
FROM dict_types dt CROSS JOIN (VALUES
  ('QUALIFIED',  '合格',   10),
  ('PENDING',    '待检',   20),
  ('DEFECTIVE',  '不合格', 30)
) AS x(code, name, seq)
WHERE dt.code = 'INVENTORY_STATUS'
  AND dt.tenant_id = '11111111-1111-1111-1111-111111111111'
ON CONFLICT (type_id, code) DO NOTHING;

-- 8. 单据类型字典
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES ('11111111-1111-1111-1111-111111111111', 'ORDER_TYPE', '订单类型', '销/销退/采/采退')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT dt.id, x.code, x.name, x.seq, 'active'
FROM dict_types dt CROSS JOIN (VALUES
  ('SO', '销售订单', 10),
  ('SR', '销退订单', 20),
  ('PO', '采购订单', 30),
  ('PR', '采退订单', 40)
) AS x(code, name, seq)
WHERE dt.code = 'ORDER_TYPE'
  AND dt.tenant_id = '11111111-1111-1111-1111-111111111111'
ON CONFLICT (type_id, code) DO NOTHING;

-- 9. 为现有 inventory 数据随机分配库存状态：70% 合格 / 20% 待检 / 10% 不合格
UPDATE inventory SET stock_status =
  CASE
    WHEN (id * 7 + 3) % 10 < 7 THEN 'QUALIFIED'
    WHEN (id * 7 + 3) % 10 < 9 THEN 'PENDING'
    ELSE 'DEFECTIVE'
  END
WHERE stock_status IS NULL OR stock_status = '';

-- 10. 索引
CREATE INDEX IF NOT EXISTS idx_orders_isred ON orders(tenant_id, is_red);
CREATE INDEX IF NOT EXISTS idx_po_isred ON purchase_orders(tenant_id, is_red);
