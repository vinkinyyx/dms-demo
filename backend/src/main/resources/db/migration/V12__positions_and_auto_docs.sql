-- ============================================================
-- V12: 销售岗位模型 + 自动建单支持 + 库存分片完善
-- ============================================================

-- 1. 销售岗位表
CREATE TABLE IF NOT EXISTS sales_positions (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    code         VARCHAR(32) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    level        INT NOT NULL,               -- 1=Director, 2=Regional, 3=Area, 4=Rep
    parent_id    BIGINT,                     -- 上级岗位
    region       VARCHAR(32),
    status       VARCHAR(16) DEFAULT 'active',
    remark       TEXT,
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT ux_position_code UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_position_parent ON sales_positions(parent_id);
CREATE INDEX IF NOT EXISTS idx_position_tenant ON sales_positions(tenant_id, level);

-- 2. 用户绑定岗位（UNIQUE：一个岗位最多 1 个用户）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='sales_position_id') THEN
        ALTER TABLE users ADD COLUMN sales_position_id BIGINT;
        CREATE UNIQUE INDEX ux_users_position ON users(tenant_id, sales_position_id)
            WHERE sales_position_id IS NOT NULL;
    END IF;
END $$;

-- 3. 经销商归属岗位
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='dealers' AND column_name='sales_position_id') THEN
        ALTER TABLE dealers ADD COLUMN sales_position_id BIGINT;
        CREATE INDEX idx_dealer_position ON dealers(sales_position_id);
    END IF;
END $$;

-- 4. 出/入库单 - 自动建单标识 + 源单关联
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='sales_outs' AND column_name='auto_created') THEN
        ALTER TABLE sales_outs ADD COLUMN auto_created BOOLEAN DEFAULT false;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='sales_outs' AND column_name='source_order_id') THEN
        ALTER TABLE sales_outs ADD COLUMN source_order_id BIGINT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='sales_outs' AND column_name='warehouse_id') THEN
        ALTER TABLE sales_outs ADD COLUMN warehouse_id BIGINT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='receipts' AND column_name='auto_created') THEN
        ALTER TABLE receipts ADD COLUMN auto_created BOOLEAN DEFAULT false;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='receipts' AND column_name='source_po_id') THEN
        ALTER TABLE receipts ADD COLUMN source_po_id BIGINT;
    END IF;
END $$;

-- 5. 订单表加 approved_at
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='orders' AND column_name='approved_at') THEN
        ALTER TABLE orders ADD COLUMN approved_at TIMESTAMPTZ;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='purchase_orders' AND column_name='approved_at') THEN
        ALTER TABLE purchase_orders ADD COLUMN approved_at TIMESTAMPTZ;
    END IF;
END $$;

-- 6. 库存复合索引（加速批次查询）
CREATE INDEX IF NOT EXISTS idx_inv_lot ON inventory(product_id, warehouse_id, stock_status, batch_no);

-- 7. 销售岗位测试数据（层级：全国总监→大区→小区→销售代表）
DO $$
DECLARE
    v_tenant UUID := '11111111-1111-1111-1111-111111111111';
    v_dir BIGINT; v_east BIGINT; v_south BIGINT; v_west BIGINT; v_north BIGINT;
    v_a1 BIGINT; v_a2 BIGINT; v_a3 BIGINT; v_a4 BIGINT; v_a5 BIGINT; v_a6 BIGINT; v_a7 BIGINT; v_a8 BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sales_positions WHERE tenant_id = v_tenant AND level = 1) THEN
        -- L1 总监
        INSERT INTO sales_positions (tenant_id, code, name, level, region)
        VALUES (v_tenant, 'POS-DIR', '全国销售总监', 1, 'ALL') RETURNING id INTO v_dir;

        -- L2 大区经理
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-E-MGR', '东区大区经理', 2, v_dir, 'east') RETURNING id INTO v_east;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-S-MGR', '南区大区经理', 2, v_dir, 'south') RETURNING id INTO v_south;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-W-MGR', '西区大区经理', 2, v_dir, 'west') RETURNING id INTO v_west;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-N-MGR', '北区大区经理', 2, v_dir, 'north') RETURNING id INTO v_north;

        -- L3 小区经理
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-E-SH', '上海小区', 3, v_east, 'east') RETURNING id INTO v_a1;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-E-JS', '江苏小区', 3, v_east, 'east') RETURNING id INTO v_a2;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-S-GD', '广东小区', 3, v_south, 'south') RETURNING id INTO v_a3;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-S-FJ', '福建小区', 3, v_south, 'south') RETURNING id INTO v_a4;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-W-SC', '四川小区', 3, v_west, 'west') RETURNING id INTO v_a5;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-W-YN', '云南小区', 3, v_west, 'west') RETURNING id INTO v_a6;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-N-BJ', '北京小区', 3, v_north, 'north') RETURNING id INTO v_a7;
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region) VALUES
            (v_tenant, 'POS-N-TJ', '天津小区', 3, v_north, 'north') RETURNING id INTO v_a8;

        -- L4 销售代表（每小区 2-3 个岗位）
        INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region)
        SELECT v_tenant, 'POS-REP-' || LPAD(row_number() OVER ()::text, 3, '0'),
               '销售代表-' || sa.name || '-' || sfx, 4, sa.id, sa.region
        FROM (SELECT id, name, region FROM sales_positions WHERE tenant_id = v_tenant AND level=3) sa
        CROSS JOIN unnest(ARRAY['一','二']) AS sfx;
    END IF;
END $$;

-- 8. 迁移旧的 sales_dealer_mapping 数据到新的 dealers.sales_position_id
UPDATE dealers d
SET sales_position_id = sp.id
FROM sales_positions sp, sales_dealer_mapping sdm, sales_users su
WHERE d.id = sdm.dealer_id
  AND sdm.sales_user_id = su.id
  AND sp.tenant_id = su.tenant_id
  AND sp.level = 4
  AND ABS(sp.id - su.id) < 500  -- 粗略配对
  AND d.sales_position_id IS NULL;

-- 均匀分配未归属的经销商到 REP 岗位
UPDATE dealers d
SET sales_position_id = sub.id
FROM (
    SELECT id, tenant_id,
           row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS rn,
           COUNT(*) OVER (PARTITION BY tenant_id) AS cnt
    FROM sales_positions WHERE level = 4
) sub
WHERE d.tenant_id = sub.tenant_id
  AND d.sales_position_id IS NULL
  AND ((d.id - 1) % sub.cnt) + 1 = sub.rn;

-- 9. 绑定 sales1 用户到某个 REP 岗位（不重复绑定）
UPDATE users SET sales_position_id = (
    SELECT id FROM sales_positions
    WHERE tenant_id = users.tenant_id AND level = 4
      AND NOT EXISTS (SELECT 1 FROM users u2 WHERE u2.sales_position_id = sales_positions.id AND u2.id != users.id)
    LIMIT 1
)
WHERE username = 'sales1' AND sales_position_id IS NULL;

UPDATE users SET sales_position_id = (
    SELECT id FROM sales_positions
    WHERE tenant_id = users.tenant_id AND level = 1
    LIMIT 1
)
WHERE username = 'director' AND sales_position_id IS NULL;
