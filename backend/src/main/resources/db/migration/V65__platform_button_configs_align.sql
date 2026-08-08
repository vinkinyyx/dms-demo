-- V65: 修正 pageKey 不一致的 button_configs seed
-- 历史 seed 中：sales-orders (DB) 对应业务 orders；sales-out (DB) 对应 sales-outs
-- 现统一按 modules.js 的 key：orders / sales-outs

-- 删除老的不一致 key
DELETE FROM platform_button_configs WHERE page_key IN ('sales-orders', 'sales-out');

-- orders (销售订单)
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'orders', 'ALL', 'toolbar', 'search', '查询', 'primary', 'sales_order:search', TRUE, 10, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,                TRUE, 20, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'toolbar', 'import', '导入', 'default', 'sales_order:import',TRUE, 30, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'toolbar', 'export', '导出', 'default', 'sales_order:export',TRUE, 40, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'toolbar', 'create', '新增', 'primary', 'sales_order:create',TRUE, 90, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'row',     'view',    '详情',     'primary', 'sales_order:view',   TRUE, 10, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'row',     'edit',    '编辑',     'primary', 'sales_order:edit',   TRUE, 20, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'row',     'submit',  '提交',     'warning', 'sales_order:submit', TRUE, 30, 'common', TRUE),
    (NULL, 'orders', 'ALL', 'row',     'approve', '审批',     'success', 'sales_order:approve',TRUE, 40, 'common', FALSE),
    (NULL, 'orders', 'ALL', 'row',     'delete',  '删除',     'danger',  'sales_order:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- sales-outs (销售出库)
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'sales-outs', 'ALL', 'toolbar', 'search', '查询', 'primary', 'sales_out:search', TRUE, 10, 'common', FALSE),
    (NULL, 'sales-outs', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,              TRUE, 20, 'common', FALSE),
    (NULL, 'sales-outs', 'ALL', 'toolbar', 'import', '导入', 'default', 'sales_out:import',TRUE, 30, 'common', FALSE),
    (NULL, 'sales-outs', 'ALL', 'toolbar', 'export', '导出', 'default', 'sales_out:export',TRUE, 40, 'common', FALSE),
    (NULL, 'sales-outs', 'ALL', 'toolbar', 'create', '新增', 'primary', 'sales_out:create',TRUE, 90, 'common', FALSE),
    (NULL, 'sales-outs', 'ALL', 'row',     'view',    '详情',     'primary', 'sales_out:view',   TRUE, 10, 'common', FALSE),
    (NULL, 'sales-outs', 'ALL', 'row',     'edit',    '编辑',     'primary', 'sales_out:edit',   TRUE, 20, 'common', FALSE),
    (NULL, 'sales-outs', 'ALL', 'row',     'confirm', '确认',     'warning', 'sales_out:confirm',TRUE, 30, 'common', TRUE),
    (NULL, 'sales-outs', 'ALL', 'row',     'cancel',  '取消',     'info',    'sales_out:cancel', TRUE, 40, 'common', TRUE),
    (NULL, 'sales-outs', 'ALL', 'row',     'delete',  '删除',     'danger',  'sales_out:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 业务页补齐 categories / dealers / warehouses / suppliers / regions / product-prices / product-lines / product-package-levels / product-bundles / contract-apps / authorizations / sales-returns / purchase-returns / inventory-adjustments / surgery-reports / promotions / roles
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'categories', 'ALL', 'toolbar', 'search', '查询', 'primary', 'product_category:search', TRUE, 10, 'common', FALSE),
    (NULL, 'categories', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'categories', 'ALL', 'toolbar', 'create', '新增', 'primary', 'product_category:create', TRUE, 90, 'common', FALSE),
    (NULL, 'categories', 'ALL', 'row',     'view',   '详情', 'primary', 'product_category:view', TRUE, 10, 'common', FALSE),
    (NULL, 'categories', 'ALL', 'row',     'edit',   '编辑', 'primary', 'product_category:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'categories', 'ALL', 'row',     'delete', '删除', 'danger',  'product_category:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'dealers', 'ALL', 'toolbar', 'search', '查询', 'primary', 'dealer:search', TRUE, 10, 'common', FALSE),
    (NULL, 'dealers', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'dealers', 'ALL', 'toolbar', 'import', '导入', 'default', 'dealer:import', TRUE, 30, 'common', FALSE),
    (NULL, 'dealers', 'ALL', 'toolbar', 'export', '导出', 'default', 'dealer:export', TRUE, 40, 'common', FALSE),
    (NULL, 'dealers', 'ALL', 'toolbar', 'create', '新增', 'primary', 'dealer:create', TRUE, 90, 'common', FALSE),
    (NULL, 'dealers', 'ALL', 'row',     'view',   '详情', 'primary', 'dealer:view', TRUE, 10, 'common', FALSE),
    (NULL, 'dealers', 'ALL', 'row',     'edit',   '编辑', 'primary', 'dealer:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'dealers', 'ALL', 'row',     'delete', '删除', 'danger',  'dealer:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'warehouses', 'ALL', 'toolbar', 'search', '查询', 'primary', 'warehouse:search', TRUE, 10, 'common', FALSE),
    (NULL, 'warehouses', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'warehouses', 'ALL', 'toolbar', 'create', '新增', 'primary', 'warehouse:create', TRUE, 90, 'common', FALSE),
    (NULL, 'warehouses', 'ALL', 'row',     'view',   '详情', 'primary', 'warehouse:view', TRUE, 10, 'common', FALSE),
    (NULL, 'warehouses', 'ALL', 'row',     'edit',   '编辑', 'primary', 'warehouse:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'warehouses', 'ALL', 'row',     'delete', '删除', 'danger',  'warehouse:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'suppliers', 'ALL', 'toolbar', 'search', '查询', 'primary', 'supplier:search', TRUE, 10, 'common', FALSE),
    (NULL, 'suppliers', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'suppliers', 'ALL', 'toolbar', 'create', '新增', 'primary', 'supplier:create', TRUE, 90, 'common', FALSE),
    (NULL, 'suppliers', 'ALL', 'row',     'view',   '详情', 'primary', 'supplier:view', TRUE, 10, 'common', FALSE),
    (NULL, 'suppliers', 'ALL', 'row',     'edit',   '编辑', 'primary', 'supplier:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'suppliers', 'ALL', 'row',     'delete', '删除', 'danger',  'supplier:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'regions', 'ALL', 'toolbar', 'search', '查询', 'primary', 'region:view', TRUE, 10, 'common', FALSE),
    (NULL, 'regions', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'regions', 'ALL', 'toolbar', 'create', '新增', 'primary', 'region:create', TRUE, 90, 'common', FALSE),
    (NULL, 'regions', 'ALL', 'row',     'edit',   '编辑', 'primary', 'region:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'regions', 'ALL', 'row',     'delete', '删除', 'danger',  'region:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'product-prices', 'ALL', 'toolbar', 'search', '查询', 'primary', 'product_price:view', TRUE, 10, 'common', FALSE),
    (NULL, 'product-prices', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'product-prices', 'ALL', 'toolbar', 'create', '新增', 'primary', 'product_price:create', TRUE, 90, 'common', FALSE),
    (NULL, 'product-prices', 'ALL', 'row',     'edit',   '编辑', 'primary', 'product_price:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'product-prices', 'ALL', 'row',     'delete', '删除', 'danger',  'product_price:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'product-lines', 'ALL', 'toolbar', 'search', '查询', 'primary', 'product_line:view', TRUE, 10, 'common', FALSE),
    (NULL, 'product-lines', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'product-lines', 'ALL', 'toolbar', 'create', '新增', 'primary', 'product_line:create', TRUE, 90, 'common', FALSE),
    (NULL, 'product-lines', 'ALL', 'row',     'edit',   '编辑', 'primary', 'product_line:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'product-lines', 'ALL', 'row',     'delete', '删除', 'danger',  'product_line:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'product-package-levels', 'ALL', 'toolbar', 'search', '查询', 'primary', 'product_package:view', TRUE, 10, 'common', FALSE),
    (NULL, 'product-package-levels', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'product-package-levels', 'ALL', 'toolbar', 'create', '新增', 'primary', 'product_package:create', TRUE, 90, 'common', FALSE),
    (NULL, 'product-package-levels', 'ALL', 'row',     'edit',   '编辑', 'primary', 'product_package:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'product-package-levels', 'ALL', 'row',     'delete', '删除', 'danger',  'product_package:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'product-bundles', 'ALL', 'toolbar', 'search', '查询', 'primary', 'product_bundle:view', TRUE, 10, 'common', FALSE),
    (NULL, 'product-bundles', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'product-bundles', 'ALL', 'toolbar', 'create', '新增', 'primary', 'product_bundle:create', TRUE, 90, 'common', FALSE),
    (NULL, 'product-bundles', 'ALL', 'row',     'edit',   '编辑', 'primary', 'product_bundle:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'product-bundles', 'ALL', 'row',     'delete', '删除', 'danger',  'product_bundle:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'contract-apps', 'ALL', 'toolbar', 'search', '查询', 'primary', 'contract_application:search', TRUE, 10, 'common', FALSE),
    (NULL, 'contract-apps', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'contract-apps', 'ALL', 'toolbar', 'create', '新增', 'primary', 'contract_application:create', TRUE, 90, 'common', FALSE),
    (NULL, 'contract-apps', 'ALL', 'row',     'view',   '详情', 'primary', 'contract_application:view', TRUE, 10, 'common', FALSE),
    (NULL, 'contract-apps', 'ALL', 'row',     'edit',   '编辑', 'primary', 'contract_application:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'contract-apps', 'ALL', 'row',     'submit', '提交审批', 'warning', 'contract_application:submit', TRUE, 30, 'common', TRUE),
    (NULL, 'contract-apps', 'ALL', 'row',     'approve','审批通过', 'success', 'contract_application:approve', TRUE, 40, 'common', FALSE),
    (NULL, 'contract-apps', 'ALL', 'row',     'delete', '删除', 'danger', 'contract_application:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'authorizations', 'ALL', 'toolbar', 'search', '查询', 'primary', 'authorization:search', TRUE, 10, 'common', FALSE),
    (NULL, 'authorizations', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'authorizations', 'ALL', 'toolbar', 'create', '新增', 'primary', 'authorization:create', TRUE, 90, 'common', FALSE),
    (NULL, 'authorizations', 'ALL', 'row',     'view',   '详情', 'primary', 'authorization:view', TRUE, 10, 'common', FALSE),
    (NULL, 'authorizations', 'ALL', 'row',     'edit',   '编辑', 'primary', 'authorization:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'authorizations', 'ALL', 'row',     'delete', '删除', 'danger',  'authorization:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'sales-returns', 'ALL', 'toolbar', 'search', '查询', 'primary', 'sales_return:search', TRUE, 10, 'common', FALSE),
    (NULL, 'sales-returns', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'sales-returns', 'ALL', 'toolbar', 'create', '新增', 'primary', 'sales_return:create', TRUE, 90, 'common', FALSE),
    (NULL, 'sales-returns', 'ALL', 'row',     'view',   '详情', 'primary', 'sales_return:view', TRUE, 10, 'common', FALSE),
    (NULL, 'sales-returns', 'ALL', 'row',     'edit',   '编辑', 'primary', 'sales_return:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'sales-returns', 'ALL', 'row',     'delete', '删除', 'danger',  'sales_return:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'purchase-returns', 'ALL', 'toolbar', 'search', '查询', 'primary', 'purchase_return:search', TRUE, 10, 'common', FALSE),
    (NULL, 'purchase-returns', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'purchase-returns', 'ALL', 'toolbar', 'create', '新增', 'primary', 'purchase_return:create', TRUE, 90, 'common', FALSE),
    (NULL, 'purchase-returns', 'ALL', 'row',     'view',   '详情', 'primary', 'purchase_return:view', TRUE, 10, 'common', FALSE),
    (NULL, 'purchase-returns', 'ALL', 'row',     'edit',   '编辑', 'primary', 'purchase_return:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'purchase-returns', 'ALL', 'row',     'delete', '删除', 'danger',  'purchase_return:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'inventory-adjustments', 'ALL', 'toolbar', 'search', '查询', 'primary', 'inventory_adjustment:search', TRUE, 10, 'common', FALSE),
    (NULL, 'inventory-adjustments', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'inventory-adjustments', 'ALL', 'toolbar', 'create', '新增', 'primary', 'inventory_adjustment:create', TRUE, 90, 'common', FALSE),
    (NULL, 'inventory-adjustments', 'ALL', 'row',     'view',   '详情', 'primary', 'inventory_adjustment:view', TRUE, 10, 'common', FALSE),
    (NULL, 'inventory-adjustments', 'ALL', 'row',     'edit',   '编辑', 'primary', 'inventory_adjustment:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'inventory-adjustments', 'ALL', 'row',     'delete', '删除', 'danger',  'inventory_adjustment:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'surgery-reports', 'ALL', 'toolbar', 'search', '查询', 'primary', 'surgery_report:search', TRUE, 10, 'common', FALSE),
    (NULL, 'surgery-reports', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'surgery-reports', 'ALL', 'toolbar', 'create', '新增', 'primary', 'surgery_report:create', TRUE, 90, 'common', FALSE),
    (NULL, 'surgery-reports', 'ALL', 'row',     'view',   '详情', 'primary', 'surgery_report:view', TRUE, 10, 'common', FALSE),
    (NULL, 'surgery-reports', 'ALL', 'row',     'edit',   '编辑', 'primary', 'surgery_report:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'surgery-reports', 'ALL', 'row',     'delete', '删除', 'danger',  'surgery_report:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'promotions', 'ALL', 'toolbar', 'search', '查询', 'primary', 'promotion:search', TRUE, 10, 'common', FALSE),
    (NULL, 'promotions', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'promotions', 'ALL', 'toolbar', 'create', '新增', 'primary', 'promotion:create', TRUE, 90, 'common', FALSE),
    (NULL, 'promotions', 'ALL', 'row',     'view',   '详情', 'primary', 'promotion:view', TRUE, 10, 'common', FALSE),
    (NULL, 'promotions', 'ALL', 'row',     'edit',   '编辑', 'primary', 'promotion:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'promotions', 'ALL', 'row',     'delete', '删除', 'danger',  'promotion:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'roles', 'ALL', 'toolbar', 'search', '查询', 'primary', 'role:search', TRUE, 10, 'common', FALSE),
    (NULL, 'roles', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL, TRUE, 20, 'common', FALSE),
    (NULL, 'roles', 'ALL', 'toolbar', 'create', '新增', 'primary', 'role:create', TRUE, 90, 'common', FALSE),
    (NULL, 'roles', 'ALL', 'row',     'view',   '详情', 'primary', 'role:view', TRUE, 10, 'common', FALSE),
    (NULL, 'roles', 'ALL', 'row',     'edit',   '编辑', 'primary', 'role:edit', TRUE, 20, 'common', FALSE),
    (NULL, 'roles', 'ALL', 'row',     'assign', '分配用户', 'warning', 'role:assign', TRUE, 30, 'common', FALSE),
    (NULL, 'roles', 'ALL', 'row',     'delete', '删除', 'danger',  'role:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- ============ rbac_resources 同步：补齐新增 button 资源（每个租户一份） ============
DO $$
DECLARE
    t_id UUID;
    bt TEXT[] := ARRAY[
        'sales_order:search', 'sales_order:create', 'sales_order:view', 'sales_order:edit',
        'sales_order:submit', 'sales_order:approve', 'sales_order:import', 'sales_order:export', 'sales_order:delete',
        'sales_out:search', 'sales_out:create', 'sales_out:view', 'sales_out:edit', 'sales_out:confirm',
        'sales_out:cancel', 'sales_out:import', 'sales_out:export', 'sales_out:delete',
        'product_category:search', 'product_category:create', 'product_category:view', 'product_category:edit', 'product_category:delete',
        'product_line:search', 'product_line:create', 'product_line:view', 'product_line:edit', 'product_line:delete',
        'product_package:search', 'product_package:create', 'product_package:view', 'product_package:edit', 'product_package:delete',
        'product_bundle:search', 'product_bundle:create', 'product_bundle:view', 'product_bundle:edit', 'product_bundle:delete',
        'product_price:search', 'product_price:create', 'product_price:view', 'product_price:edit', 'product_price:delete',
        'region:search', 'region:create', 'region:view', 'region:edit', 'region:delete',
        'supplier:search', 'supplier:create', 'supplier:view', 'supplier:edit', 'supplier:delete',
        'warehouse:search', 'warehouse:create', 'warehouse:view', 'warehouse:edit', 'warehouse:delete',
        'contract_application:search', 'contract_application:create', 'contract_application:view', 'contract_application:edit',
        'contract_application:submit', 'contract_application:approve', 'contract_application:delete',
        'authorization:search', 'authorization:create', 'authorization:view', 'authorization:edit', 'authorization:delete',
        'sales_return:search', 'sales_return:create', 'sales_return:view', 'sales_return:edit', 'sales_return:delete',
        'purchase_return:search', 'purchase_return:create', 'purchase_return:view', 'purchase_return:edit', 'purchase_return:delete',
        'inventory_adjustment:search', 'inventory_adjustment:create', 'inventory_adjustment:view', 'inventory_adjustment:edit', 'inventory_adjustment:delete',
        'surgery_report:search', 'surgery_report:create', 'surgery_report:view', 'surgery_report:edit', 'surgery_report:delete',
        'promotion:search', 'promotion:create', 'promotion:view', 'promotion:edit', 'promotion:delete',
        'role:search', 'role:create', 'role:view', 'role:edit', 'role:assign', 'role:delete'
    ];
    code_one TEXT;
    inserted INT := 0;
BEGIN
    FOR t_id IN SELECT id FROM tenants WHERE id <> '00000000-0000-0000-0000-000000000000'::uuid AND deleted_at IS NULL LOOP
        FOREACH code_one IN ARRAY bt LOOP
            IF NOT EXISTS (SELECT 1 FROM resources WHERE tenant_id = t_id AND code = code_one AND deleted_at IS NULL) THEN
                INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
                VALUES (t_id, code_one, code_one, 'button', ARRAY['read','write']::varchar[], NULL, 'active', now(), now());
                inserted := inserted + 1;
            END IF;
        END LOOP;
    END LOOP;
    RAISE NOTICE 'V65 inserted % button resources', inserted;
END $$;
