-- V67: fix menu/resource codes, restore dealer profile entry, repair API log filters
CREATE TEMP TABLE v67_menu_resources(code varchar(128), name varchar(200), path varchar(500), seed_codes text[]) ON COMMIT DROP;
INSERT INTO v67_menu_resources(code, name, path, seed_codes) VALUES
  ('dashboard:view', '?????', '/dashboard', ARRAY['dealer:view', 'role:view', 'menu.dashboard', 'report:view']),
  ('products:view', '????', '/products', ARRAY['product:view', 'product_category:view', 'product_price:view']),
  ('report_sales_ranking:view', '??????', '/reports?key=sales-ranking', ARRAY['report:view']),
  ('report_product_top10:view', '???? TOP10', '/reports?key=product-top10', ARRAY['report:view']),
  ('report_inventory_turnover:view', '????', '/reports?key=inventory-turnover', ARRAY['report:view']),
  ('report_surgery_stats:view', '??????', '/reports?key=surgery-stats', ARRAY['report:view']),
  ('report_receivables:view', '????', '/reports?key=receivables', ARRAY['report:view']),
  ('report_order_trace:view', '????', '/reports?key=order-trace', ARRAY['report:view']);

INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, r.code, r.name, 'menu', ARRAY['view']::varchar[], r.path, 'active', now(), now()
FROM tenants t CROSS JOIN v67_menu_resources r
WHERE NOT EXISTS (
  SELECT 1 FROM resources x WHERE x.tenant_id = t.id AND x.code = r.code AND x.deleted_at IS NULL
);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT sr_base.strategy_id, r_new.id, ARRAY['view']::varchar[], now()
FROM v67_menu_resources m
JOIN resources r_new ON r_new.code = m.code AND r_new.deleted_at IS NULL
JOIN resources r_seed ON r_seed.tenant_id = r_new.tenant_id AND r_seed.deleted_at IS NULL AND r_seed.code = ANY(m.seed_codes)
JOIN strategy_resources sr_base ON sr_base.resource_id = r_seed.id
WHERE NOT EXISTS (
  SELECT 1 FROM strategy_resources x WHERE x.strategy_id = sr_base.strategy_id AND x.resource_id = r_new.id
);

INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, 'api_log:view', '??????', 'menu', ARRAY['view']::varchar[], '/api-call-logs', 'active', now(), now()
FROM tenants t
WHERE NOT EXISTS (
  SELECT 1 FROM resources x WHERE x.tenant_id = t.id AND x.code = 'api_log:view' AND x.deleted_at IS NULL
);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT sr_base.strategy_id, r_new.id, ARRAY['view']::varchar[], now()
FROM strategy_resources sr_base
JOIN resources r_role ON r_role.id = sr_base.resource_id AND r_role.code = 'role:view' AND r_role.deleted_at IS NULL
JOIN resources r_new ON r_new.tenant_id = r_role.tenant_id AND r_new.code = 'api_log:view' AND r_new.deleted_at IS NULL
WHERE NOT EXISTS (
  SELECT 1 FROM strategy_resources x WHERE x.strategy_id = sr_base.strategy_id AND x.resource_id = r_new.id
);

INSERT INTO platform_filter_configs (page_key, tenant_type, filter_key, label, component_type, dict_type, multiple, visible, sort_order, status)
VALUES ('api-call-log', 'ALL', 'status', '??', 'select', 'api_call_status', FALSE, TRUE, 40, 'active')
ON CONFLICT (page_key, tenant_type, filter_key) DO UPDATE
SET label = EXCLUDED.label, component_type = EXCLUDED.component_type, dict_type = EXCLUDED.dict_type,
    visible = TRUE, status = 'active', updated_at = now();

UPDATE platform_filter_configs
SET visible = FALSE, status = 'inactive', updated_at = now()
WHERE page_key = 'api-call-log' AND tenant_type = 'ALL' AND filter_key = 'statusCode';

UPDATE tenant_filter_configs
SET label = '??', component_type = 'select', dict_type = 'api_call_status', visible = TRUE, status = 'active', updated_at = now()
WHERE page_key = 'api-call-log' AND filter_key = 'status';

UPDATE tenant_filter_configs
SET visible = FALSE, status = 'inactive', updated_at = now()
WHERE page_key = 'api-call-log' AND filter_key = 'statusCode';

INSERT INTO dict_types (tenant_id, code, name, description)
SELECT t.id, 'api_call_status', '??????', '?????? HTTP ???'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM dict_types dt WHERE dt.tenant_id = t.id AND dt.code = 'api_call_status');

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT dt.id, v.code, v.name, v.seq, 'active'
FROM dict_types dt
CROSS JOIN (VALUES
  ('200', '200 ??', 10),
  ('400', '400 ????', 20),
  ('401', '401 ???', 30),
  ('403', '403 ????', 40),
  ('404', '404 ???', 50),
  ('500', '500 ?????', 60)
) AS v(code, name, seq)
WHERE dt.code = 'api_call_status'
  AND NOT EXISTS (SELECT 1 FROM dict_items di WHERE di.type_id = dt.id AND di.code = v.code);

INSERT INTO platform_button_configs (tenant_id, page_key, tenant_type, scope, button_key, label, button_type, permission_code, visible, sort_order, row_button_position, confirm_required, created_at, updated_at)
VALUES (NULL, 'dealer-profile', 'ALL', 'row', 'view', '????', 'primary', 'dealer:view', TRUE, 10, 'common', FALSE, now(), now())
ON CONFLICT (page_key, scope, button_key) WHERE tenant_id IS NULL DO UPDATE
SET label = EXCLUDED.label, button_type = EXCLUDED.button_type, permission_code = EXCLUDED.permission_code,
    visible = TRUE, sort_order = EXCLUDED.sort_order, row_button_position = EXCLUDED.row_button_position,
    confirm_required = EXCLUDED.confirm_required, status = 'active', updated_at = now();

UPDATE platform_button_configs
SET visible = FALSE, status = 'inactive', updated_at = now()
WHERE tenant_id IS NULL AND page_key = 'dealer-profile' AND scope = 'toolbar' AND button_key IN ('import', 'export', 'create');

UPDATE platform_button_configs
SET visible = TRUE, status = 'active', permission_code = 'dealer:view', button_type = 'primary', label = '????', sort_order = 10, updated_at = now()
WHERE tenant_id IS NULL AND page_key = 'dealer-profile' AND scope = 'row' AND button_key = 'view';

UPDATE platform_button_configs
SET visible = TRUE, status = 'active', permission_code = 'dealer:view', button_type = 'primary', label = '????', sort_order = 10, updated_at = now()
WHERE page_key = 'dealer-profile' AND scope = 'row' AND button_key = 'view';

UPDATE platform_button_configs
SET visible = FALSE, status = 'inactive', updated_at = now()
WHERE page_key = 'dealer-profile' AND scope = 'toolbar' AND button_key IN ('import', 'export', 'create');
