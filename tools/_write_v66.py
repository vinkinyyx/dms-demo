from pathlib import Path
content = r'''-- V66: DMS v3.8.9 列表页租户覆盖补齐与默认数据修正
-- 1) 筛选字段支持租户级覆盖：平台默认保留 platform_filter_configs，租户覆盖写入 tenant_filter_configs。
CREATE TABLE IF NOT EXISTS tenant_filter_configs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    page_key VARCHAR(100) NOT NULL,
    filter_key VARCHAR(100) NOT NULL,
    label VARCHAR(100) NOT NULL,
    component_type VARCHAR(32) NOT NULL,
    dict_type VARCHAR(100),
    default_value TEXT,
    multiple BOOLEAN NOT NULL DEFAULT FALSE,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_tenant_filter_configs UNIQUE (tenant_id, page_key, filter_key)
);
CREATE INDEX IF NOT EXISTS idx_tenant_filter_configs_lookup ON tenant_filter_configs(tenant_id, page_key, status, sort_order);

-- 2) 修正平台默认筛选字段中文编码。
UPDATE platform_filter_configs SET label = '关键词', updated_at = now() WHERE filter_key = 'keyword';
UPDATE platform_filter_configs SET label = '状态', updated_at = now() WHERE filter_key = 'status';
UPDATE platform_filter_configs SET label = '开始日期', updated_at = now() WHERE filter_key IN ('dateFrom','startDate');
UPDATE platform_filter_configs SET label = '结束日期', updated_at = now() WHERE filter_key IN ('dateTo','endDate');
UPDATE platform_filter_configs SET label = '经销商', updated_at = now() WHERE filter_key IN ('dealer','dealerId');
UPDATE platform_filter_configs SET label = '医院', updated_at = now() WHERE filter_key IN ('hospital','hospitalId');
UPDATE platform_filter_configs SET label = '仓库', updated_at = now() WHERE filter_key IN ('warehouse','warehouseId');
UPDATE platform_filter_configs SET label = '供应商', updated_at = now() WHERE filter_key IN ('supplier','supplierId');
UPDATE platform_filter_configs SET label = '产品', updated_at = now() WHERE filter_key IN ('product','productId');
UPDATE platform_filter_configs SET label = '级别', updated_at = now() WHERE filter_key = 'level';
UPDATE platform_filter_configs SET label = '区域', updated_at = now() WHERE filter_key = 'region';

-- 3) 修正平台默认按钮中文编码。
UPDATE platform_button_configs SET label = '查询', updated_at = now() WHERE button_key = 'search' AND scope = 'toolbar';
UPDATE platform_button_configs SET label = '重置', updated_at = now() WHERE button_key = 'reset' AND scope = 'toolbar';
UPDATE platform_button_configs SET label = '导入', updated_at = now() WHERE button_key = 'import' AND scope = 'toolbar';
UPDATE platform_button_configs SET label = '导出', updated_at = now() WHERE button_key = 'export' AND scope = 'toolbar';
UPDATE platform_button_configs SET label = '新增', updated_at = now() WHERE button_key = 'create' AND scope = 'toolbar';
UPDATE platform_button_configs SET label = '详情', updated_at = now() WHERE button_key = 'view' AND scope = 'row';
UPDATE platform_button_configs SET label = '查看画像', updated_at = now() WHERE page_key = 'dealer-profile' AND button_key = 'view' AND scope = 'row';
UPDATE platform_button_configs SET label = '编辑', updated_at = now() WHERE button_key = 'edit' AND scope = 'row';
UPDATE platform_button_configs SET label = '删除', updated_at = now() WHERE button_key = 'delete' AND scope = 'row';
UPDATE platform_button_configs SET label = '提交', updated_at = now() WHERE button_key = 'submit' AND scope = 'row';
UPDATE platform_button_configs SET label = '审批', updated_at = now() WHERE button_key = 'approve' AND scope = 'row';
UPDATE platform_button_configs SET label = '驳回', updated_at = now() WHERE button_key = 'reject' AND scope = 'row';
UPDATE platform_button_configs SET label = '取消', updated_at = now() WHERE button_key = 'cancel' AND scope = 'row';
UPDATE platform_button_configs SET label = '确认', updated_at = now() WHERE button_key = 'confirm' AND scope = 'row';
UPDATE platform_button_configs SET label = '打开', updated_at = now() WHERE button_key = 'open' AND scope = 'row';

-- 4) 经销商画像只保留查询/重置/查看画像，不提供导入/导出/新增。
UPDATE platform_button_configs SET visible = FALSE, updated_at = now()
WHERE tenant_id IS NULL AND page_key = 'dealer-profile' AND scope = 'toolbar' AND button_key IN ('import','export','create');

-- 5) 销售订单补齐驳回、取消，和前端旧业务动作保持一致。
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required, created_at, updated_at)
VALUES
(NULL, 'orders', 'ALL', 'row', 'reject', '驳回', 'danger', 'sales_order:reject', TRUE, 50, 'danger', TRUE, now(), now()),
(NULL, 'orders', 'ALL', 'row', 'cancel', '取消', 'warning', 'sales_order:cancel', TRUE, 60, 'common', TRUE, now(), now())
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO UPDATE
SET label = EXCLUDED.label, button_type = EXCLUDED.button_type, permission_code = EXCLUDED.permission_code,
    visible = EXCLUDED.visible, sort_order = EXCLUDED.sort_order, row_button_position = EXCLUDED.row_button_position,
    confirm_required = EXCLUDED.confirm_required, updated_at = now();

-- 6) 补齐销售订单新增按钮权限资源。
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, codes.code, codes.name, 'button', '["view"]'::jsonb, NULL, 'active', now(), now()
FROM tenants t
CROSS JOIN (VALUES
    ('sales_order:reject', '销售订单驳回'),
    ('sales_order:cancel', '销售订单取消')
) AS codes(code, name)
WHERE NOT EXISTS (
    SELECT 1 FROM resources r WHERE r.tenant_id = t.id AND r.code = codes.code AND r.deleted_at IS NULL
);
'''
Path('backend/src/main/resources/db/migration/V66__tenant_filter_override_and_layout_fixes.sql').write_text(content, encoding='utf-8', newline='\n')
