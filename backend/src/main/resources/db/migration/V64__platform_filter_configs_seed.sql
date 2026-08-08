-- V64: filter_configs 灌种 — D13 列表页布局统一规范（让 ListPageLayout 搜索区有数据）
-- 规则：每个 pageKey 至少 keyword 搜索字段；按业务页面补充 select/date 字段。
-- 关键唯一键：UNIQUE (page_key, tenant_type, filter_key)

-- ============ 工具：所有 pageKey 统一加 keyword 搜索字段（input） ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status)
SELECT k.page_key, 'ALL', 'keyword', '关键词', 'input', NULL, FALSE, TRUE, 10, 'active'
FROM (VALUES
  ('products'),('categories'),('dealers'),('hospitals'),('warehouses'),
  ('suppliers'),('regions'),('product-prices'),('product-lines'),
  ('product-package-levels'),('product-bundles'),
  ('contract-apps'),('contracts'),('authorizations'),
  ('orders'),('sales-returns'),('purchase-orders'),('purchase-returns'),
  ('inventory'),('sales-outs'),('receipts'),('stock-moves'),('inventory-adjustments'),
  ('surgery-reports'),('promotions'),
  ('dealer-profile'),
  ('positions'),('users'),('roles'),('api-call-log'),
  ('product-mappings')
) AS k(page_key)
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 销售订单：状态 + 日期范围 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('orders', 'ALL', 'status',   '状态',     'select',     'sales_order_status', FALSE, TRUE, 20, 'active'),
  ('orders', 'ALL', 'dateFrom', '起始日期', 'date',       NULL, FALSE, TRUE, 30, 'active'),
  ('orders', 'ALL', 'dateTo',   '截止日期', 'date',       NULL, FALSE, TRUE, 40, 'active'),
  ('orders', 'ALL', 'dealer',   '经销商',   'select',     'dealer',     FALSE, TRUE, 50, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 采购订单 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('purchase-orders', 'ALL', 'status',   '状态',     'select', 'purchase_order_status', FALSE, TRUE, 20, 'active'),
  ('purchase-orders', 'ALL', 'dateFrom', '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('purchase-orders', 'ALL', 'dateTo',   '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active'),
  ('purchase-orders', 'ALL', 'supplier', '供应商',   'select', 'supplier', FALSE, TRUE, 50, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 销退 / 采退 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('sales-returns',    'ALL', 'status',   '状态',     'select', 'sales_return_status',    FALSE, TRUE, 20, 'active'),
  ('sales-returns',    'ALL', 'dateFrom', '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('sales-returns',    'ALL', 'dateTo',   '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active'),
  ('purchase-returns', 'ALL', 'status',   '状态',     'select', 'purchase_return_status', FALSE, TRUE, 20, 'active'),
  ('purchase-returns', 'ALL', 'dateFrom', '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('purchase-returns', 'ALL', 'dateTo',   '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 销售出库 / 收货入库 / 库存移动 / 库存调整 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('sales-outs',              'ALL', 'status',   '状态',     'select', 'sales_out_status',     FALSE, TRUE, 20, 'active'),
  ('sales-outs',              'ALL', 'dateFrom', '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('sales-outs',              'ALL', 'dateTo',   '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active'),
  ('receipts',                'ALL', 'status',   '状态',     'select', 'receipt_status',       FALSE, TRUE, 20, 'active'),
  ('receipts',                'ALL', 'dateFrom', '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('receipts',                'ALL', 'dateTo',   '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active'),
  ('stock-moves',             'ALL', 'status',   '状态',     'select', 'stock_move_status',    FALSE, TRUE, 20, 'active'),
  ('stock-moves',             'ALL', 'dateFrom', '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('stock-moves',             'ALL', 'dateTo',   '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active'),
  ('inventory-adjustments',   'ALL', 'status',   '状态',     'select', 'inventory_adjust_status', FALSE, TRUE, 20, 'active'),
  ('inventory',               'ALL', 'warehouse', '仓库',    'select', 'warehouse',            FALSE, TRUE, 20, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 主数据：产品 / 经销商 / 医院 / 仓库 / 供应商 / 区域 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('products',  'ALL', 'status',     '状态',     'select', 'product_status',     FALSE, TRUE, 20, 'active'),
  ('products',  'ALL', 'category',    '产品分类', 'select', 'product_category',   FALSE, TRUE, 30, 'active'),
  ('products',  'ALL', 'productType', '产品类型', 'select', 'product_type',       FALSE, TRUE, 40, 'active'),
  ('dealers',   'ALL', 'status',     '状态',     'select', 'dealer_status',      FALSE, TRUE, 20, 'active'),
  ('dealers',   'ALL', 'level',      '级别',     'select', 'dealer_level',       FALSE, TRUE, 30, 'active'),
  ('hospitals', 'ALL', 'status',     '状态',     'select', 'hospital_status',    FALSE, TRUE, 20, 'active'),
  ('hospitals', 'ALL', 'level',      '医院等级', 'select', 'hospital_level',     FALSE, TRUE, 30, 'active'),
  ('warehouses','ALL', 'status',     '状态',     'select', 'warehouse_status',   FALSE, TRUE, 20, 'active'),
  ('suppliers', 'ALL', 'status',     '状态',     'select', 'supplier_status',    FALSE, TRUE, 20, 'active'),
  ('regions',   'ALL', 'parentId',   '父级区域', 'select', 'region',             FALSE, TRUE, 20, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 合同/授权 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('contract-apps',  'ALL', 'status',   '状态',     'select', 'contract_app_status',   FALSE, TRUE, 20, 'active'),
  ('contracts',       'ALL', 'status',   '状态',     'select', 'contract_status',       FALSE, TRUE, 20, 'active'),
  ('contracts',       'ALL', 'dateFrom', '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('contracts',       'ALL', 'dateTo',   '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active'),
  ('authorizations',  'ALL', 'status',   '状态',     'select', 'authorization_status',  FALSE, TRUE, 20, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 手术报台 / 促销 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('surgery-reports', 'ALL', 'status',    '状态',   'select', 'surgery_report_status', FALSE, TRUE, 20, 'active'),
  ('surgery-reports', 'ALL', 'dateFrom',  '起始日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('surgery-reports', 'ALL', 'dateTo',    '截止日期', 'date',   NULL, FALSE, TRUE, 40, 'active'),
  ('promotions',      'ALL', 'status',    '状态',     'select', 'promotion_status',     FALSE, TRUE, 20, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 经销商画像 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('dealer-profile', 'ALL', 'level',   '级别',   'select', 'dealer_level',   FALSE, TRUE, 20, 'active'),
  ('dealer-profile', 'ALL', 'region',  '区域',   'select', 'region',         FALSE, TRUE, 30, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;

-- ============ 账号/角色/接口日志 ============
INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status) VALUES
  ('users',          'ALL', 'status',     '状态',     'select', 'user_status',         FALSE, TRUE, 20, 'active'),
  ('roles',          'ALL', 'tenantType', '租户类型', 'select', 'tenant_type',         FALSE, TRUE, 20, 'active'),
  ('api-call-log',   'ALL', 'dateFrom',   '起始日期', 'date',   NULL, FALSE, TRUE, 20, 'active'),
  ('api-call-log',   'ALL', 'dateTo',     '截止日期', 'date',   NULL, FALSE, TRUE, 30, 'active'),
  ('api-call-log',   'ALL', 'status',     '状态',     'select', 'api_call_status',     FALSE, TRUE, 40, 'active'),
  ('product-mappings','ALL','status',     '状态',     'select', 'product_mapping_status', FALSE, TRUE, 20, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO NOTHING;