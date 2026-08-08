-- V62: 给所有列表页灌 platform_button_configs 种子（D13 配套）
-- 规则：每个 pageKey 至少 3 个 toolbar 按钮（search/reset/create）+ 至少 1 个 row 按钮（view）
-- 业务页可扩展（import/export/submit/approve/delete 等）。
-- 字段 permissionCode 与 V61 中 rbac_resources.code 一一对应（V61 已补齐 button 资源）。

-- ============ helper：用 INSERT ... ON CONFLICT 防止重复 ============
-- 平台默认（tenant_id IS NULL）的唯一键：UNIQUE (page_key, scope, button_key) WHERE tenant_id IS NULL

-- 经销商画像（dealer-profile）：已 V59 预置，这里再确认 ON CONFLICT 跳过
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'search',  '查询',   'primary', 'dealer:search',  TRUE,  10, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'reset',   '重置',   'default', NULL,            TRUE,  20, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'import',  '导入',   'default', 'dealer:import',  TRUE,  30, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'export',  '导出',   'default', 'dealer:export',  TRUE,  40, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'toolbar', 'create',  '新增',   'primary', 'dealer:create',  TRUE,  90, 'common', FALSE),
    (NULL, 'dealer-profile', 'ALL', 'row',     'view',    '查看画像', 'primary', 'dealer:view',    TRUE,  10, 'common', FALSE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 销售订单（sales-orders）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'sales-orders', 'ALL', 'toolbar', 'search', '查询', 'primary', 'sales_order:search', TRUE, 10, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,                TRUE, 20, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'toolbar', 'import', '导入', 'default', 'sales_order:import',TRUE, 30, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'toolbar', 'export', '导出', 'default', 'sales_order:export',TRUE, 40, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'toolbar', 'create', '新增', 'primary', 'sales_order:create',TRUE, 90, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'row',     'view',    '详情',     'primary', 'sales_order:view',   TRUE, 10, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'row',     'edit',    '编辑',     'primary', 'sales_order:edit',   TRUE, 20, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'row',     'submit',  '提交',     'warning', 'sales_order:submit', TRUE, 30, 'common', TRUE),
    (NULL, 'sales-orders', 'ALL', 'row',     'approve', '审批',     'success', 'sales_order:approve',TRUE, 40, 'common', FALSE),
    (NULL, 'sales-orders', 'ALL', 'row',     'delete',  '删除',     'danger',  'sales_order:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 采购订单（purchase-orders）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'purchase-orders', 'ALL', 'toolbar', 'search', '查询', 'primary', 'purchase_order:search', TRUE, 10, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,                   TRUE, 20, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'toolbar', 'import', '导入', 'default', 'purchase_order:import', TRUE, 30, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'toolbar', 'export', '导出', 'default', 'purchase_order:export', TRUE, 40, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'toolbar', 'create', '新增', 'primary', 'purchase_order:create', TRUE, 90, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'row',     'view',    '详情', 'primary', 'purchase_order:view',   TRUE, 10, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'row',     'edit',    '编辑', 'primary', 'purchase_order:edit',   TRUE, 20, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'row',     'submit',  '提交', 'warning', 'purchase_order:submit', TRUE, 30, 'common', TRUE),
    (NULL, 'purchase-orders', 'ALL', 'row',     'approve', '审批', 'success', 'purchase_order:approve',TRUE, 40, 'common', FALSE),
    (NULL, 'purchase-orders', 'ALL', 'row',     'delete',  '删除', 'danger',  'purchase_order:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 收货入库（receipts）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'receipts', 'ALL', 'toolbar', 'search', '查询', 'primary', 'receipt:search', TRUE, 10, 'common', FALSE),
    (NULL, 'receipts', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,            TRUE, 20, 'common', FALSE),
    (NULL, 'receipts', 'ALL', 'toolbar', 'export', '导出', 'default', 'receipt:export',TRUE, 40, 'common', FALSE),
    (NULL, 'receipts', 'ALL', 'toolbar', 'create', '新增', 'primary', 'receipt:create',TRUE, 90, 'common', FALSE),
    (NULL, 'receipts', 'ALL', 'row',     'view',    '详情', 'primary', 'receipt:view',  TRUE, 10, 'common', FALSE),
    (NULL, 'receipts', 'ALL', 'row',     'edit',    '编辑', 'primary', 'receipt:edit',  TRUE, 20, 'common', FALSE),
    (NULL, 'receipts', 'ALL', 'row',     'confirm', '确认', 'success', 'receipt:confirm', TRUE, 30, 'common', TRUE),
    (NULL, 'receipts', 'ALL', 'row',     'cancel',  '取消', 'warning', 'receipt:cancel', TRUE, 40, 'common', TRUE),
    (NULL, 'receipts', 'ALL', 'row',     'delete',  '删除', 'danger',  'receipt:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 销售出库（sales-out）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'sales-out', 'ALL', 'toolbar', 'search', '查询', 'primary', 'sales_out:search', TRUE, 10, 'common', FALSE),
    (NULL, 'sales-out', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,              TRUE, 20, 'common', FALSE),
    (NULL, 'sales-out', 'ALL', 'toolbar', 'export', '导出', 'default', 'sales_out:export',TRUE, 40, 'common', FALSE),
    (NULL, 'sales-out', 'ALL', 'toolbar', 'create', '新增', 'primary', 'sales_out:create',TRUE, 90, 'common', FALSE),
    (NULL, 'sales-out', 'ALL', 'row',     'view',    '详情', 'primary', 'sales_out:view',  TRUE, 10, 'common', FALSE),
    (NULL, 'sales-out', 'ALL', 'row',     'edit',    '编辑', 'primary', 'sales_out:edit',  TRUE, 20, 'common', FALSE),
    (NULL, 'sales-out', 'ALL', 'row',     'confirm', '确认', 'success', 'sales_out:confirm', TRUE, 30, 'common', TRUE),
    (NULL, 'sales-out', 'ALL', 'row',     'cancel',  '取消', 'warning', 'sales_out:cancel', TRUE, 40, 'common', TRUE),
    (NULL, 'sales-out', 'ALL', 'row',     'delete',  '删除', 'danger',  'sales_out:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 库存查询（inventory）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'inventory', 'ALL', 'toolbar', 'search', '查询', 'primary', 'inventory:search', TRUE, 10, 'common', FALSE),
    (NULL, 'inventory', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,               TRUE, 20, 'common', FALSE),
    (NULL, 'inventory', 'ALL', 'toolbar', 'export', '导出', 'default', 'inventory:export', TRUE, 40, 'common', FALSE),
    (NULL, 'inventory', 'ALL', 'toolbar', 'adjust', '库存调整', 'warning', 'inventory:adjust', TRUE, 95, 'common', FALSE),
    (NULL, 'inventory', 'ALL', 'row',     'view',   '详情',  'primary', 'inventory:view',   TRUE, 10, 'common', FALSE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 库存移动（stock-moves）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'stock-moves', 'ALL', 'toolbar', 'search', '查询', 'primary', 'stock_move:search', TRUE, 10, 'common', FALSE),
    (NULL, 'stock-moves', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,                TRUE, 20, 'common', FALSE),
    (NULL, 'stock-moves', 'ALL', 'toolbar', 'create', '新增', 'primary', 'stock_move:create', TRUE, 90, 'common', FALSE),
    (NULL, 'stock-moves', 'ALL', 'row',     'view',    '详情', 'primary', 'stock_move:view',   TRUE, 10, 'common', FALSE),
    (NULL, 'stock-moves', 'ALL', 'row',     'confirm', '确认', 'success', 'stock_move:confirm',TRUE, 30, 'common', TRUE),
    (NULL, 'stock-moves', 'ALL', 'row',     'cancel',  '取消', 'warning', 'stock_move:cancel', TRUE, 40, 'common', TRUE),
    (NULL, 'stock-moves', 'ALL', 'row',     'delete',  '删除', 'danger',  'stock_move:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 产品（products）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'products', 'ALL', 'toolbar', 'search', '查询', 'primary', 'product:search', TRUE, 10, 'common', FALSE),
    (NULL, 'products', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,             TRUE, 20, 'common', FALSE),
    (NULL, 'products', 'ALL', 'toolbar', 'import', '导入', 'default', 'product:import', TRUE, 30, 'common', FALSE),
    (NULL, 'products', 'ALL', 'toolbar', 'export', '导出', 'default', 'product:export', TRUE, 40, 'common', FALSE),
    (NULL, 'products', 'ALL', 'toolbar', 'create', '新增', 'primary', 'product:create', TRUE, 90, 'common', FALSE),
    (NULL, 'products', 'ALL', 'row',     'view',   '详情', 'primary', 'product:view',  TRUE, 10, 'common', FALSE),
    (NULL, 'products', 'ALL', 'row',     'edit',   '编辑', 'primary', 'product:edit',  TRUE, 20, 'common', FALSE),
    (NULL, 'products', 'ALL', 'row',     'delete', '删除', 'danger',  'product:delete',TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 产品对码（product-mappings）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'product-mappings', 'ALL', 'toolbar', 'search', '查询', 'primary', 'product_mapping:search', TRUE, 10, 'common', FALSE),
    (NULL, 'product-mappings', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,                    TRUE, 20, 'common', FALSE),
    (NULL, 'product-mappings', 'ALL', 'toolbar', 'import', '导入', 'default', 'product_mapping:import', TRUE, 30, 'common', FALSE),
    (NULL, 'product-mappings', 'ALL', 'toolbar', 'export', '导出', 'default', 'product_mapping:export', TRUE, 40, 'common', FALSE),
    (NULL, 'product-mappings', 'ALL', 'row',     'view',   '查看对码', 'primary', 'product_mapping:view', TRUE, 10, 'common', FALSE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 医院/终端（hospitals）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'hospitals', 'ALL', 'toolbar', 'search', '查询', 'primary', 'hospital:search', TRUE, 10, 'common', FALSE),
    (NULL, 'hospitals', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,             TRUE, 20, 'common', FALSE),
    (NULL, 'hospitals', 'ALL', 'toolbar', 'import', '导入', 'default', 'hospital:import', TRUE, 30, 'common', FALSE),
    (NULL, 'hospitals', 'ALL', 'toolbar', 'export', '导出', 'default', 'hospital:export', TRUE, 40, 'common', FALSE),
    (NULL, 'hospitals', 'ALL', 'toolbar', 'create', '新增', 'primary', 'hospital:create', TRUE, 90, 'common', FALSE),
    (NULL, 'hospitals', 'ALL', 'row',     'view',   '详情', 'primary', 'hospital:view',  TRUE, 10, 'common', FALSE),
    (NULL, 'hospitals', 'ALL', 'row',     'edit',   '编辑', 'primary', 'hospital:edit',  TRUE, 20, 'common', FALSE),
    (NULL, 'hospitals', 'ALL', 'row',     'delete', '删除', 'danger',  'hospital:delete',TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 合同（contracts）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'contracts', 'ALL', 'toolbar', 'search', '查询', 'primary', 'contract:search', TRUE, 10, 'common', FALSE),
    (NULL, 'contracts', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,             TRUE, 20, 'common', FALSE),
    (NULL, 'contracts', 'ALL', 'toolbar', 'export', '导出', 'default', 'contract:export',TRUE, 40, 'common', FALSE),
    (NULL, 'contracts', 'ALL', 'toolbar', 'create', '新增', 'primary', 'contract:create',TRUE, 90, 'common', FALSE),
    (NULL, 'contracts', 'ALL', 'row',     'view',    '详情', 'primary', 'contract:view',   TRUE, 10, 'common', FALSE),
    (NULL, 'contracts', 'ALL', 'row',     'edit',    '编辑', 'primary', 'contract:edit',   TRUE, 20, 'common', FALSE),
    (NULL, 'contracts', 'ALL', 'row',     'submit',  '提交', 'warning', 'contract:submit', TRUE, 30, 'common', TRUE),
    (NULL, 'contracts', 'ALL', 'row',     'approve', '审批', 'success', 'contract:approve',TRUE, 40, 'common', FALSE),
    (NULL, 'contracts', 'ALL', 'row',     'delete',  '删除', 'danger',  'contract:delete', TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 报表（reports）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'reports', 'ALL', 'toolbar', 'search', '查询', 'primary', 'report:view',  TRUE, 10, 'common', FALSE),
    (NULL, 'reports', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,           TRUE, 20, 'common', FALSE),
    (NULL, 'reports', 'ALL', 'toolbar', 'export', '导出', 'default', 'report:export',TRUE, 40, 'common', FALSE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- API 调用日志（api-call-log）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'api-call-log', 'ALL', 'toolbar', 'search', '查询', 'primary', 'api_log:search', TRUE, 10, 'common', FALSE),
    (NULL, 'api-call-log', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,            TRUE, 20, 'common', FALSE),
    (NULL, 'api-call-log', 'ALL', 'toolbar', 'export', '导出', 'default', 'api_log:export',TRUE, 40, 'common', FALSE),
    (NULL, 'api-call-log', 'ALL', 'row',     'view',   '详情', 'primary', 'api_log:view',  TRUE, 10, 'common', FALSE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 销售岗位（positions）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'positions', 'ALL', 'toolbar', 'search', '查询', 'primary', 'position:search', TRUE, 10, 'common', FALSE),
    (NULL, 'positions', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,             TRUE, 20, 'common', FALSE),
    (NULL, 'positions', 'ALL', 'toolbar', 'create', '新增', 'primary', 'position:create', TRUE, 90, 'common', FALSE),
    (NULL, 'positions', 'ALL', 'row',     'view',    '详情',     'primary', 'position:view',        TRUE, 10, 'common', FALSE),
    (NULL, 'positions', 'ALL', 'row',     'edit',    '编辑',     'primary', 'position:edit',        TRUE, 20, 'common', FALSE),
    (NULL, 'positions', 'ALL', 'row',     'bind_user','分配销售','warning', 'position:bind_user',   TRUE, 30, 'common', FALSE),
    (NULL, 'positions', 'ALL', 'row',     'bind_dealer','分配经销商','info', 'position:bind_dealer',TRUE, 40, 'common', FALSE),
    (NULL, 'positions', 'ALL', 'row',     'delete',  '删除',     'danger',  'position:delete',      TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- 用户（users）
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'users', 'ALL', 'toolbar', 'search', '查询', 'primary', 'user:search', TRUE, 10, 'common', FALSE),
    (NULL, 'users', 'ALL', 'toolbar', 'reset',  '重置', 'default', NULL,          TRUE, 20, 'common', FALSE),
    (NULL, 'users', 'ALL', 'toolbar', 'create', '新增', 'primary', 'user:create', TRUE, 90, 'common', FALSE),
    (NULL, 'users', 'ALL', 'row',     'view',    '详情',     'primary', 'user:view',           TRUE, 10, 'common', FALSE),
    (NULL, 'users', 'ALL', 'row',     'edit',    '编辑',     'primary', 'user:edit',           TRUE, 20, 'common', FALSE),
    (NULL, 'users', 'ALL', 'row',     'reset_pwd','重置密码','warning', 'user:reset_password', TRUE, 30, 'common', TRUE),
    (NULL, 'users', 'ALL', 'row',     'unlock',  '解锁',     'success', 'user:unlock',         TRUE, 40, 'common', FALSE),
    (NULL, 'users', 'ALL', 'row',     'delete',  '删除',     'danger',  'user:delete',         TRUE, 90, 'danger', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;

-- ============ filter_configs 灌种（让 ListPageLayout 搜索区有数据） ============
-- 给每个 pageKey 灌至少 keyword 搜索字段
-- 这里只灌关键字搜索；其他字段留给 admin-vue 配置
-- 产品对码（product-mappings）补充：行内"启用/停用"按钮
INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required) VALUES
    (NULL, 'product-mappings', 'ALL', 'row', 'toggle', '启用/停用', 'warning', 'product_mapping:view', TRUE, 20, 'common', TRUE)
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO NOTHING;