-- ============================================================
-- V11: 销售组织架构 + 手术植入报台 + 序列号管理 + 三角色权限
-- ============================================================

-- 1. products 加序列号管理标识
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='products' AND column_name='is_serial_managed') THEN
        ALTER TABLE products ADD COLUMN is_serial_managed BOOLEAN DEFAULT false;
    END IF;
END $$;

-- 2. users 加 role + dealer_id
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='role') THEN
        ALTER TABLE users ADD COLUMN role VARCHAR(32) DEFAULT 'admin';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='dealer_id') THEN
        ALTER TABLE users ADD COLUMN dealer_id BIGINT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='sales_user_id') THEN
        ALTER TABLE users ADD COLUMN sales_user_id BIGINT;
    END IF;
END $$;

-- 唯一约束：每个 dealer 只能绑定 1 个 dealer 账号
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_dealer_binding
  ON users(tenant_id, dealer_id) WHERE role = 'dealer' AND dealer_id IS NOT NULL;

-- 3. 销售组织架构表
CREATE TABLE IF NOT EXISTS sales_users (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    name          VARCHAR(64) NOT NULL,
    code          VARCHAR(32),
    level         VARCHAR(32) NOT NULL,  -- DIRECTOR/REGIONAL/AREA/REP
    parent_id     BIGINT,                -- 自关联
    region        VARCHAR(32),           -- east/south/west/north
    phone         VARCHAR(32),
    email         VARCHAR(128),
    status        VARCHAR(16) DEFAULT 'active',
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_sales_users_parent ON sales_users(parent_id);
CREATE INDEX IF NOT EXISTS idx_sales_users_tenant ON sales_users(tenant_id, level);

-- 4. 销售-经销商 归属映射
CREATE TABLE IF NOT EXISTS sales_dealer_mapping (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    sales_user_id  BIGINT NOT NULL,
    dealer_id      BIGINT NOT NULL,
    since_date     DATE DEFAULT CURRENT_DATE,
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sdm_dealer
  ON sales_dealer_mapping(tenant_id, dealer_id);
CREATE INDEX IF NOT EXISTS idx_sdm_sales
  ON sales_dealer_mapping(sales_user_id);

-- 5. 手术植入报台主表
CREATE TABLE IF NOT EXISTS surgery_reports (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    code            VARCHAR(64) UNIQUE,
    dealer_id       BIGINT NOT NULL,
    terminal_id     BIGINT NOT NULL,   -- 医院
    warehouse_id    BIGINT,
    sales_user_id   BIGINT,             -- 报台的销售
    surgery_date    DATE NOT NULL,
    patient_info    VARCHAR(200),
    doctor_name     VARCHAR(64),
    status          VARCHAR(32) DEFAULT 'COMPLETED',
    remark          TEXT,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_surgery_tenant ON surgery_reports(tenant_id, surgery_date DESC);
CREATE INDEX IF NOT EXISTS idx_surgery_dealer ON surgery_reports(dealer_id);

-- 6. 手术明细
CREATE TABLE IF NOT EXISTS surgery_report_lines (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL REFERENCES surgery_reports(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL,
    qty             NUMERIC(14,4) NOT NULL,
    batch_no        VARCHAR(64),
    serial_no       VARCHAR(64),
    unit_price      NUMERIC(18,2),
    remark          TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sr_lines_report ON surgery_report_lines(report_id);

-- 7. 示例：销售组织架构数据
DO $$
DECLARE
    v_tenant UUID := '11111111-1111-1111-1111-111111111111';
    v_director BIGINT;
    v_east BIGINT; v_south BIGINT; v_west BIGINT; v_north BIGINT;
    v_area1 BIGINT; v_area2 BIGINT; v_area3 BIGINT; v_area4 BIGINT;
    v_rep1 BIGINT; v_rep2 BIGINT; v_rep3 BIGINT;
BEGIN
    -- 幂等
    IF NOT EXISTS (SELECT 1 FROM sales_users WHERE tenant_id = v_tenant AND level='DIRECTOR') THEN
        INSERT INTO sales_users (tenant_id, name, code, level, region) VALUES
            (v_tenant, '张总监', 'D001', 'DIRECTOR', 'ALL') RETURNING id INTO v_director;

        -- 4 大区
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '李东区', 'RE001', 'REGIONAL', v_director, 'east') RETURNING id INTO v_east;
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '王南区', 'RS001', 'REGIONAL', v_director, 'south') RETURNING id INTO v_south;
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '赵西区', 'RW001', 'REGIONAL', v_director, 'west') RETURNING id INTO v_west;
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '钱北区', 'RN001', 'REGIONAL', v_director, 'north') RETURNING id INTO v_north;

        -- 8 小区（4大区各 2）
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '孙上海区', 'A001', 'AREA', v_east, 'east') RETURNING id INTO v_area1;
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '周江苏区', 'A002', 'AREA', v_east, 'east') RETURNING id INTO v_area2;
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '吴广东区', 'A003', 'AREA', v_south, 'south') RETURNING id INTO v_area3;
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '郑福建区', 'A004', 'AREA', v_south, 'south') RETURNING id INTO v_area4;
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '冯四川区', 'A005', 'AREA', v_west, 'west'),
            (v_tenant, '陈云南区', 'A006', 'AREA', v_west, 'west'),
            (v_tenant, '褚北京区', 'A007', 'AREA', v_north, 'north'),
            (v_tenant, '卫天津区', 'A008', 'AREA', v_north, 'north');

        -- 销售代表 16 名（8 小区各 2）
        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region) VALUES
            (v_tenant, '代表_上海_张一', 'R001', 'REP', v_area1, 'east'),
            (v_tenant, '代表_上海_李二', 'R002', 'REP', v_area1, 'east'),
            (v_tenant, '代表_江苏_王三', 'R003', 'REP', v_area2, 'east'),
            (v_tenant, '代表_江苏_赵四', 'R004', 'REP', v_area2, 'east'),
            (v_tenant, '代表_广东_钱五', 'R005', 'REP', v_area3, 'south'),
            (v_tenant, '代表_广东_孙六', 'R006', 'REP', v_area3, 'south'),
            (v_tenant, '代表_福建_周七', 'R007', 'REP', v_area4, 'south'),
            (v_tenant, '代表_福建_吴八', 'R008', 'REP', v_area4, 'south');

        INSERT INTO sales_users (tenant_id, name, code, level, parent_id, region)
        SELECT v_tenant, '代表_' || sa.name || '_' || sfx, 'R00'||row_number() over () + 8, 'REP', sa.id, sa.region
        FROM (SELECT id, name, region FROM sales_users WHERE tenant_id = v_tenant AND level='AREA' AND code >= 'A005') sa
        CROSS JOIN unnest(ARRAY['一','二']) AS sfx;
    END IF;
END $$;

-- 8. 销售-经销商映射：将现有 dealers 均匀分配给销售代表
INSERT INTO sales_dealer_mapping (tenant_id, sales_user_id, dealer_id)
SELECT d.tenant_id, s.id, d.id
FROM (
    SELECT id, tenant_id, row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS rn
    FROM dealers WHERE deleted_at IS NULL
) d
JOIN (
    SELECT id, tenant_id, row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS rn,
           COUNT(*) OVER (PARTITION BY tenant_id) AS cnt
    FROM sales_users WHERE level='REP'
) s ON s.tenant_id = d.tenant_id AND ((d.rn - 1) % s.cnt) + 1 = s.rn
ON CONFLICT DO NOTHING;

-- 9. 部分产品设为序列号管理（每 5 个中选 1 个）
UPDATE products SET is_serial_managed = true
WHERE id % 5 = 0 AND is_serial_managed IS DISTINCT FROM true;

-- 10. 创建测试账号 (bcrypt hash of 'Sh123456')
-- 依赖：现有 admin 账号已存在
DO $$
DECLARE
    v_tenant UUID := '11111111-1111-1111-1111-111111111111';
    v_dealer1 BIGINT;
    v_dealer2 BIGINT;
    v_sales_rep BIGINT;
BEGIN
    -- admin 账号已存在于 V1 - 更新其 role
    UPDATE users SET role = 'admin' WHERE username = 'admin' AND tenant_id = v_tenant;

    -- 找到 REP 代表 R001
    SELECT id INTO v_sales_rep FROM sales_users WHERE tenant_id = v_tenant AND code='R001' LIMIT 1;

    -- 创建销售账号 sales1 (对应 R001 代表)
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'sales1' AND tenant_id = v_tenant) THEN
        INSERT INTO users (tenant_id, username, name, user_type, password_hash, role, sales_user_id, must_change_password, email, status)
        VALUES (v_tenant, 'sales1', '代表_上海_张一', 'vendor',
                '$2a$10$3NBBjXk8UfHY26vRy6Kzp.WOozxbC8Kd7lkZoNSlwYcO7Q0BiZR8G',
                'sales', v_sales_rep, false, 'sales1@dms.com', 'active');
    END IF;

    -- 找到销售总监
    SELECT id INTO v_sales_rep FROM sales_users WHERE tenant_id = v_tenant AND code='D001' LIMIT 1;
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'director' AND tenant_id = v_tenant) THEN
        INSERT INTO users (tenant_id, username, name, user_type, password_hash, role, sales_user_id, must_change_password, email, status)
        VALUES (v_tenant, 'director', '张总监', 'vendor',
                '$2a$10$3NBBjXk8UfHY26vRy6Kzp.WOozxbC8Kd7lkZoNSlwYcO7Q0BiZR8G',
                'sales', v_sales_rep, false, 'director@dms.com', 'active');
    END IF;

    -- 找 dealer1 / dealer2
    SELECT id INTO v_dealer1 FROM dealers WHERE tenant_id = v_tenant ORDER BY id LIMIT 1;
    SELECT id INTO v_dealer2 FROM dealers WHERE tenant_id = v_tenant ORDER BY id OFFSET 1 LIMIT 1;

    -- 创建经销商账号 dealer1
    IF v_dealer1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'dealer1' AND tenant_id = v_tenant) THEN
        INSERT INTO users (tenant_id, username, name, user_type, password_hash, role, dealer_id, must_change_password, email, status)
        VALUES (v_tenant, 'dealer1', '经销商A账号', 'dealer',
                '$2a$10$3NBBjXk8UfHY26vRy6Kzp.WOozxbC8Kd7lkZoNSlwYcO7Q0BiZR8G',
                'dealer', v_dealer1, false, 'dealer1@dms.com', 'active');
    END IF;
    IF v_dealer2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'dealer2' AND tenant_id = v_tenant) THEN
        INSERT INTO users (tenant_id, username, name, user_type, password_hash, role, dealer_id, must_change_password, email, status)
        VALUES (v_tenant, 'dealer2', '经销商B账号', 'dealer',
                '$2a$10$3NBBjXk8UfHY26vRy6Kzp.WOozxbC8Kd7lkZoNSlwYcO7Q0BiZR8G',
                'dealer', v_dealer2, false, 'dealer2@dms.com', 'active');
    END IF;
END $$;
