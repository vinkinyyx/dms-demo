-- v3.8.6 销售岗位绑定：销售账号业绩占比 + 统一使用关联表
-- 1. position_users 增加业绩分配占比（0~1，同一岗位下所有人员占比总和 <= 1）
ALTER TABLE position_users ADD COLUMN IF NOT EXISTS share_ratio NUMERIC(8,4) NOT NULL DEFAULT 0;

-- 2. 将历史上写入 users.sales_position_id 的绑定回填到 position_users（去重，占比默认 0，待人工调整）
INSERT INTO position_users (tenant_id, position_id, user_id, role_type, share_ratio, created_at)
SELECT u.tenant_id, u.sales_position_id, u.id, 'sales', 0, now()
FROM users u
WHERE u.sales_position_id IS NOT NULL
  AND u.role = 'sales'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM position_users pu WHERE pu.tenant_id = u.tenant_id AND pu.user_id = u.id
  );

-- 3. 将历史上写入 dealers.sales_position_id 的归属回填到 position_dealers
INSERT INTO position_dealers (tenant_id, position_id, dealer_id, created_at)
SELECT d.tenant_id, d.sales_position_id, d.id, now()
FROM dealers d
WHERE d.sales_position_id IS NOT NULL
  AND d.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM position_dealers pd WHERE pd.tenant_id = d.tenant_id AND pd.dealer_id = d.id
  );
