-- V18: v3.4.15 菜单配置表 + 测试数据精简（仓库软删除保留少量）
-- 说明：
--   1) 新增 menu_configs 表，支持管理员在后台调整每个菜单所属分组与排序（后端下发覆盖前端硬编码归属）
--   2) 测试数据精简：仓库当前 150 条（50 经销商×3），软删除多余，仅保留每类少量，避免破坏既有库存/单据 FK 引用

-- ============ 1. 菜单配置表 ============
CREATE TABLE IF NOT EXISTS menu_configs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    menu_key VARCHAR(64) NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    label VARCHAR(64),
    icon VARCHAR(16),
    sort_order INT NOT NULL DEFAULT 100,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, menu_key)
);
CREATE INDEX IF NOT EXISTS idx_menu_configs_tenant ON menu_configs(tenant_id, group_name, sort_order);

-- ============ 2. 测试数据精简：仓库 ============
-- 每个"仓库类型"仅保留前 2 个（按 id 升序），其余软删除。
-- 采用软删除（deleted_at）而非物理删除，避免破坏 inventory / receipts / stock_moves 等 FK 引用。
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY type ORDER BY id) AS rn
    FROM warehouses
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
      AND deleted_at IS NULL
)
UPDATE warehouses w
SET deleted_at = now(), status = 'inactive'
FROM ranked r
WHERE w.id = r.id AND r.rn > 2;
