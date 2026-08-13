-- V83: Ensure SYS_ADMIN has frontend menu permission aliases required by Vue MENU_GROUPS/CrudView.
-- Some older strategies only contain fine-grained or legacy resource codes. Add the missing
-- menu codes explicitly so the test super-admin can see all modules and page action buttons.

INSERT INTO resources (tenant_id, code, name, type, parent_id, operations, path, status, created_at, updated_at, version, deleted_at)
SELECT
  '11111111-1111-1111-1111-111111111111',
  code,
  name,
  'api',
  NULL,
  ARRAY['view','create','edit','delete','search','export','import','manage','admin'],
  path,
  'active',
  now(),
  now(),
  0,
  NULL
FROM (VALUES
  ('dealer:view',              '经销商查看',          '/api/dealers/**'),
  ('hospital:view',            '医院终端查看',        '/api/hospitals/**'),
  ('contract:view',            '合同查看',            '/api/contracts/**'),
  ('contract_template:manage', '合同模板管理',        '/api/contracts/templates/**'),
  ('sales_order:view',         '销售订单查看',        '/api/orders/**'),
  ('purchase_order:view',      '采购订单查看',        '/api/purchase-orders/**'),
  ('inventory:view',           '库存查询查看',        '/api/inventory/**'),
  ('sales_out:view',           '销售出库查看',        '/api/sales-outs/**'),
  ('receipt:view',             '收货入库查看',        '/api/receipts/**'),
  ('stock_move:view',          '库存移动查看',        '/api/stock-moves/**'),
  ('approval:manage',          '审批流配置管理',      '/api/approval/templates/**'),
  ('approval:admin',           '审批监控管理',        '/api/approval/admin/**'),
  ('position:view',            '销售岗位查看',        '/api/positions/**'),
  ('tenant_ui_config:view',    '列表页配置查看',      '/api/tenant-page-configs/**'),
  ('api_log:view',             '接口日志查看',        '/api/api-call-logs/**'),
  ('email_log:view',           '邮件日志查看',        '/api/email-logs/**')
) AS v(code, name, path)
WHERE NOT EXISTS (
  SELECT 1
  FROM resources r
  WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
    AND r.code = v.code
    AND r.deleted_at IS NULL
);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT 1, r.id, ARRAY['view','create','edit','delete','search','export','import','manage','admin'], now()
FROM resources r
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND r.code IN (
    'dealer:view','hospital:view','contract:view','contract_template:manage',
    'sales_order:view','purchase_order:view','inventory:view','sales_out:view',
    'receipt:view','stock_move:view','approval:manage','approval:admin',
    'position:view','tenant_ui_config:view','api_log:view','email_log:view'
  )
  AND r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM strategy_resources sr WHERE sr.strategy_id = 1 AND sr.resource_id = r.id
  );
