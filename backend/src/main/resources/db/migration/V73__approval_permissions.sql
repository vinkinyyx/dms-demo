-- V73: 审批中心菜单与权限资源，并授予租户管理员（拥有 tenant_ui_config:view 的策略）
CREATE TEMP TABLE v73_approval_resources(code varchar(128), name varchar(200), path varchar(500), seed_code varchar(128)) ON COMMIT PRESERVE ROWS;
INSERT INTO v73_approval_resources(code, name, path, seed_code) VALUES
  ('approval:todo',      '我的审批', '/approval/todo',       NULL),
  ('approval:manage',    '审批流配置', '/approval/templates', 'tenant_ui_config:view'),
  ('approval:delegation','审批委托', '/approval/delegations','tenant_ui_config:view'),
  ('approval:admin',     '审批监控', '/approval/admin',      'tenant_ui_config:view'),
  ('approval:approve',   '审批处理', NULL,                    NULL),
  ('approval:template:edit','编辑审批流', NULL,               'tenant_ui_config:view');

-- 我的审批：所有登录用户都应可见，挂到拥有 dealer:view 或 user:view 的策略上
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, r.code, r.name, 'menu', ARRAY['view']::varchar[], r.path, 'active', now(), now()
FROM tenants t CROSS JOIN v73_approval_resources r
WHERE r.seed_code IS NULL AND r.path IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM resources x WHERE x.tenant_id = t.id AND x.code = r.code AND x.deleted_at IS NULL);

-- 其余需要管理员权限的资源
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, r.code, r.name, CASE WHEN r.path IS NULL THEN 'button' ELSE 'menu' END,
       ARRAY['view']::varchar[], r.path, 'active', now(), now()
FROM tenants t CROSS JOIN v73_approval_resources r
WHERE r.seed_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM resources x WHERE x.tenant_id = t.id AND x.code = r.code AND x.deleted_at IS NULL);

-- “我的审批”授权给所有已拥有任意菜单资源的策略（确保普通用户可见）
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT sr_base.strategy_id, r_new.id, ARRAY['view']::varchar[], now()
FROM resources r_new
JOIN resources r_seed ON r_seed.tenant_id = r_new.tenant_id AND r_seed.deleted_at IS NULL
JOIN strategy_resources sr_base ON sr_base.resource_id = r_seed.id
WHERE r_new.code = 'approval:todo' AND r_new.deleted_at IS NULL
  AND r_seed.code IN ('dealer:view','user:view','sales_order:view','purchase_order:view')
  AND NOT EXISTS (SELECT 1 FROM strategy_resources x WHERE x.strategy_id = sr_base.strategy_id AND x.resource_id = r_new.id);

-- 管理类资源：跟随 tenant_ui_config:view（租户管理员）
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT sr_base.strategy_id, r_new.id, ARRAY['view']::varchar[], now()
FROM v73_approval_resources m
JOIN resources r_new ON r_new.code = m.code AND r_new.deleted_at IS NULL
JOIN resources r_seed ON r_seed.tenant_id = r_new.tenant_id AND r_seed.deleted_at IS NULL AND r_seed.code = m.seed_code
JOIN strategy_resources sr_base ON sr_base.resource_id = r_seed.id
WHERE m.seed_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM strategy_resources x WHERE x.strategy_id = sr_base.strategy_id AND x.resource_id = r_new.id);