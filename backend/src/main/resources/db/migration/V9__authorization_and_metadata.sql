-- ============================================================
-- V9: 授权重构 + 调拨/调整/收货 完善 + 通用元数据字段
-- 覆盖:
--   1. 授权表增加 product_lines 和 terminal_ids 字段（多值，逗号分隔）
--   2. inventory_adjustments 表结构完善（补 category/type 列）
--   3. stock_moves 表结构完善
--   4. 各表默认排序索引（created_at DESC / updated_at DESC）
-- ============================================================

-- 1. 授权表：加多产品线 + 多医院字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='authorizations' AND column_name='product_lines') THEN
        ALTER TABLE authorizations ADD COLUMN product_lines VARCHAR(500);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='authorizations' AND column_name='terminal_ids') THEN
        ALTER TABLE authorizations ADD COLUMN terminal_ids VARCHAR(1000);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='authorizations' AND column_name='remark') THEN
        ALTER TABLE authorizations ADD COLUMN remark TEXT;
    END IF;
END $$;

-- 2. 调整单：补齐字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='inventory_adjustments') THEN
        CREATE TABLE inventory_adjustments (
            id            BIGSERIAL PRIMARY KEY,
            tenant_id     UUID NOT NULL,
            code          VARCHAR(64) UNIQUE NOT NULL,
            warehouse_id  BIGINT,
            category      VARCHAR(16) DEFAULT 'IN',    -- IN / OUT
            type          VARCHAR(32) DEFAULT 'STOCKTAKE', -- STOCKTAKE/DAMAGE/CORRECT/OTHER
            status        VARCHAR(16) DEFAULT 'DRAFT',
            remark        TEXT,
            created_at    TIMESTAMPTZ DEFAULT now(),
            updated_at    TIMESTAMPTZ DEFAULT now(),
            created_by    BIGINT,
            updated_by    BIGINT,
            version       INT DEFAULT 0,
            deleted_at    TIMESTAMPTZ
        );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='adjustment_lines') THEN
        CREATE TABLE adjustment_lines (
            id              BIGSERIAL PRIMARY KEY,
            adjustment_id   BIGINT REFERENCES inventory_adjustments(id) ON DELETE CASCADE,
            product_id      BIGINT,
            qty             NUMERIC(14,4),
            remark          TEXT,
            created_at      TIMESTAMPTZ DEFAULT now()
        );
    END IF;
END $$;

-- 3. 索引：加速按修改时间倒序查询
CREATE INDEX IF NOT EXISTS idx_orders_updated       ON orders(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_po_updated           ON purchase_orders(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_dealers_updated      ON dealers(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_updated     ON products(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_hospitals_updated    ON hospitals(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_warehouses_updated   ON warehouses(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_authorizations_updated ON authorizations(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_contracts_updated    ON contracts(tenant_id, updated_at DESC);

-- 4. products 表加 product_line 快速字段（值同 PRODUCT_LINE 字典编码：IMPLANT/REAGENT/CONSUMABLE/EQUIPMENT）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='products' AND column_name='product_line') THEN
        ALTER TABLE products ADD COLUMN product_line VARCHAR(32);
    END IF;
END $$;

-- 5. 演示数据：为现有产品分配 product_line（基于 category_id 或随机）
UPDATE products SET product_line = 'IMPLANT'
  WHERE tenant_id IS NOT NULL AND (product_line IS NULL OR product_line = '') AND id % 4 = 0;
UPDATE products SET product_line = 'REAGENT'
  WHERE tenant_id IS NOT NULL AND (product_line IS NULL OR product_line = '') AND id % 4 = 1;
UPDATE products SET product_line = 'CONSUMABLE'
  WHERE tenant_id IS NOT NULL AND (product_line IS NULL OR product_line = '') AND id % 4 = 2;
UPDATE products SET product_line = 'EQUIPMENT'
  WHERE tenant_id IS NOT NULL AND (product_line IS NULL OR product_line = '') AND id % 4 = 3;
