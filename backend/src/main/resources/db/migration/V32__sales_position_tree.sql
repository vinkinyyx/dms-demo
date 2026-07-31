-- ============================================================
-- V32: 销售岗位树状结构 + 账号-岗位多对多（v3.7.3）
-- ============================================================
-- 1. 销售岗位加 sort_order 列（兄弟节点排序）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='sales_positions' AND column_name='sort_order') THEN
        ALTER TABLE sales_positions ADD COLUMN sort_order INT DEFAULT 0;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_position_sort ON sales_positions(tenant_id, parent_id, sort_order);

-- 2. 销售岗位关联用户（多对多：position_users）
--    同一岗位可绑定多个销售/经销商账号；同一账号只能属于一个岗位
CREATE TABLE IF NOT EXISTS position_users (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    position_id  BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    role_type    VARCHAR(16) NOT NULL,   -- 'sales' / 'dealer'
    created_at   TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT ux_position_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_pu_position FOREIGN KEY (position_id) REFERENCES sales_positions(id) ON DELETE CASCADE,
    CONSTRAINT fk_pu_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_pu_position ON position_users(tenant_id, position_id);
CREATE INDEX IF NOT EXISTS idx_pu_user ON position_users(tenant_id, user_id);

-- 3. 迁移：把 users.sales_position_id 已有绑定写入 position_users
INSERT INTO position_users (tenant_id, position_id, user_id, role_type, created_at)
SELECT u.tenant_id, u.sales_position_id, u.id,
       CASE WHEN u.role = 'sales' THEN 'sales' WHEN u.role = 'dealer' THEN 'dealer' ELSE 'sales' END,
       now()
FROM users u
WHERE u.sales_position_id IS NOT NULL
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM position_users pu
    WHERE pu.tenant_id = u.tenant_id AND pu.user_id = u.id
  );

-- 4. 经销商账号绑定岗位（多对多：position_dealers）
CREATE TABLE IF NOT EXISTS position_dealers (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    position_id  BIGINT NOT NULL,
    dealer_id    BIGINT NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT ux_position_dealer UNIQUE (tenant_id, dealer_id),
    CONSTRAINT fk_pd_position FOREIGN KEY (position_id) REFERENCES sales_positions(id) ON DELETE CASCADE,
    CONSTRAINT fk_pd_dealer FOREIGN KEY (dealer_id) REFERENCES dealers(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_pd_position ON position_dealers(tenant_id, position_id);
CREATE INDEX IF NOT EXISTS idx_pd_dealer ON position_dealers(tenant_id, dealer_id);

-- 5. 迁移：把 dealers.sales_position_id 已有绑定写入 position_dealers
INSERT INTO position_dealers (tenant_id, position_id, dealer_id, created_at)
SELECT d.tenant_id, d.sales_position_id, d.id, now()
FROM dealers d
WHERE d.sales_position_id IS NOT NULL
  AND d.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM position_dealers pd
    WHERE pd.tenant_id = d.tenant_id AND pd.dealer_id = d.id
  );

-- 6. 更新现有岗位 sort_order（按 level, id 升序赋 0/10/20/...）
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY level, id) * 10 AS rn
    FROM sales_positions
    WHERE deleted_at IS NULL
)
UPDATE sales_positions sp
SET sort_order = ordered.rn
FROM ordered
WHERE sp.id = ordered.id;
