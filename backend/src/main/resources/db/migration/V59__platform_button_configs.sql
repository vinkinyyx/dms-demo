-- V59: 列表页按钮配置（platform_button_configs）— D13 列表页布局统一规范
-- 说明：
--   1) 新增 platform_button_configs 表，管理每个页面顶部工具栏 + 行内操作的按钮
--   2) 双层覆盖模型：
--      - tenant_id IS NULL  => 平台默认（管理员在 admin-vue 预置）
--      - tenant_id = 租户ID => 租户级覆盖（租户管理员按需调整）
--   3) 唯一键：UNIQUE NULLS NOT DISTINCT (tenant_id, page_key, scope, button_key)
--      - PostgreSQL 15+ 唯一约束允许多个 NULL，使用 NULLS NOT DISTINCT 保证每个 (page_key, scope, button_key) 平台默认仅一条
--      - 同一个 (page_key, scope, button_key) 在某租户下也仅能有一条覆盖记录
--   4) 兼容现有 V50/V51 的 platform_filter_configs / platform_page_configs 风格
--   5) 配套 DTO: ButtonConfigDTO / ButtonConfigUpsertRequest；Service: PlatformButtonConfigService
--   6) 前端: ListPageLayout.vue + v-has 指令；后台: UiConfigs.vue 增加"按钮配置"Tab

-- ============ 1. 主表 ============
CREATE TABLE IF NOT EXISTS platform_button_configs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID,
    page_key VARCHAR(100) NOT NULL,
    tenant_type VARCHAR(16) NOT NULL DEFAULT 'ALL',
    scope VARCHAR(16) NOT NULL,                 -- toolbar / row
    button_key VARCHAR(100) NOT NULL,           -- 例如：search / reset / import / export / create / view / edit / submit / approve / delete
    label VARCHAR(64) NOT NULL,                 -- 按钮显示文字
    button_type VARCHAR(16) NOT NULL DEFAULT 'default',  -- primary / default / danger / warning / info / success
    icon VARCHAR(64),
    permission_code VARCHAR(128),               -- 与 role_template_resources.actions 关联
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 100,
    row_button_position VARCHAR(16) NOT NULL DEFAULT 'common',  -- row 专用：common / danger
    confirm_required BOOLEAN NOT NULL DEFAULT FALSE,            -- 是否需要二次确认（删除类）
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 唯一键：同一 (page_key, scope, button_key) 平台默认仅一条、租户覆盖仅一条
CREATE UNIQUE INDEX IF NOT EXISTS ux_pbc_default
    ON platform_button_configs(page_key, scope, button_key)
    WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_pbc_tenant
    ON platform_button_configs(tenant_id, page_key, scope, button_key)
    WHERE tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pbc_tenant_page_scope
    ON platform_button_configs(tenant_id, page_key, scope, status, sort_order);

COMMENT ON TABLE platform_button_configs IS '平台/租户级按钮配置：搜索区按钮、行内操作按钮，按 tenant_id 区分平台默认与租户覆盖';
COMMENT ON COLUMN platform_button_configs.tenant_id IS 'NULL=平台默认；非NULL=租户级覆盖';
COMMENT ON COLUMN platform_button_configs.scope IS 'toolbar=顶部工具栏；row=行内操作';
COMMENT ON COLUMN platform_button_configs.permission_code IS '与 role_template_resources.actions 关联，v-has 指令判定';

-- ============ 2. 预置示例数据：经销商申请表（与用户截图一致） ============
-- 平台默认：搜索按钮 + 重置 + 新增；行内：详情/编辑/提交审批/审批通过/删除
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required)
VALUES
    -- toolbar
    (NULL, 'dealer-applications', 'ALL', 'toolbar', 'search',  '查询',   'primary', 'dealer_application:search',  TRUE,  10, 'common', FALSE),
    (NULL, 'dealer-applications', 'ALL', 'toolbar', 'reset',   '重置',   'default', NULL,                       TRUE,  20, 'common', FALSE),
    (NULL, 'dealer-applications', 'ALL', 'toolbar', 'create',  '新增',   'primary', 'dealer_application:create',  TRUE,  90, 'common', FALSE),
    -- row
    (NULL, 'dealer-applications', 'ALL', 'row',     'view',    '详情',     'primary', 'dealer_application:view',    TRUE,  10, 'common', FALSE),
    (NULL, 'dealer-applications', 'ALL', 'row',     'edit',    '编辑',     'primary', 'dealer_application:edit',    TRUE,  20, 'common', FALSE),
    (NULL, 'dealer-applications', 'ALL', 'row',     'submit',  '提交审批', 'warning', 'dealer_application:submit',  TRUE,  30, 'common', TRUE),
    (NULL, 'dealer-applications', 'ALL', 'row',     'approve', '审批通过', 'success', 'dealer_application:approve', TRUE,  40, 'common', FALSE),
    (NULL, 'dealer-applications', 'ALL', 'row',     'delete',  '删除',     'danger',  'dealer_application:delete',  TRUE,  90, 'danger', TRUE)
ON CONFLICT DO NOTHING;
-- 经销商画像（dealer-profile）默认按钮
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required)
VALUES
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'search',  '查询',   'primary', 'dealer:search',  TRUE,  10, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'reset',   '重置',   'default', NULL,            TRUE,  20, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'import',  '导入',   'default', 'dealer:import',  TRUE,  30, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'export',  '导出',   'default', 'dealer:export',  TRUE,  40, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'create',  '新增',   'primary', 'dealer:create',  TRUE,  90, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'row',     'view',    '查看画像', 'primary', 'dealer:view',    TRUE,  10, 'common', FALSE)
ON CONFLICT DO NOTHING;