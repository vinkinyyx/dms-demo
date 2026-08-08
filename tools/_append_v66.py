from pathlib import Path
p=Path('backend/src/main/resources/db/migration/V66__tenant_filter_override_and_layout_fixes.sql')
with p.open('a', encoding='utf-8', newline='\n') as f:
    f.write('''

-- 7) 新增租户页面配置菜单权限，并授予已拥有角色管理权限的策略。
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, 'tenant_ui_config:view', '列表页配置', 'menu', '["view"]'::jsonb, '/tenant-page-configs', 'active', now(), now()
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM resources r WHERE r.tenant_id = t.id AND r.code = 'tenant_ui_config:view' AND r.deleted_at IS NULL
);

INSERT INTO strategy_resources (strategy_id, resource_id)
SELECT DISTINCT sr.strategy_id, r_new.id
FROM strategy_resources sr
JOIN resources r_role ON r_role.id = sr.resource_id AND r_role.code = 'role:view' AND r_role.deleted_at IS NULL
JOIN resources r_new ON r_new.tenant_id = r_role.tenant_id AND r_new.code = 'tenant_ui_config:view' AND r_new.deleted_at IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM strategy_resources sr2 WHERE sr2.strategy_id = sr.strategy_id AND sr2.resource_id = r_new.id
);

-- 8) 新增按钮/菜单资源后，同步给已经拥有 sales_order:view 的策略。
INSERT INTO strategy_resources (strategy_id, resource_id)
SELECT DISTINCT sr.strategy_id, r_new.id
FROM strategy_resources sr
JOIN resources r_base ON r_base.id = sr.resource_id AND r_base.code = 'sales_order:view' AND r_base.deleted_at IS NULL
JOIN resources r_new ON r_new.tenant_id = r_base.tenant_id AND r_new.code IN ('sales_order:reject','sales_order:cancel') AND r_new.deleted_at IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM strategy_resources sr2 WHERE sr2.strategy_id = sr.strategy_id AND sr2.resource_id = r_new.id
);
''')
