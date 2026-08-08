-- V52: 平台多租户演示 seed（非生产数据）
-- 覆盖：厂家/经销商租户、dealer 主数据与绑定、租户管理员、默认角色/资源/数据权限、厂家与经销商产品、产品对码。
-- 幂等：使用 WHERE NOT EXISTS / ON CONFLICT，可重复执行。
-- 所有账号初始密码 Sh123456（bcrypt cost 10），首次登录强制改密。平台管理员 admin 由 V51 提供。

-- ========== 1. 租户 ==========
INSERT INTO tenants (id, code, name, industry, timezone, status, tenant_type, deployment_mode,
                     modules_enabled, quota, attrs, enabled_at, created_at, updated_at, version)
SELECT v.* FROM (VALUES
  ('11111111-0000-0000-0000-000000000001'::uuid, 'MFR_A', '厂家A', '医疗器械', 'Asia/Shanghai', 'active', 'MANUFACTURER', 'SHARED',
   '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, now(), now(), now(), 0),
  ('11111111-0000-0000-0000-000000000002'::uuid, 'MFR_B', '厂家B', '医疗器械', 'Asia/Shanghai', 'active', 'MANUFACTURER', 'SHARED',
   '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, now(), now(), now(), 0),
  ('22222222-0000-0000-0000-000000000001'::uuid, 'DEALER_A1', '经销商A1', '医疗器械', 'Asia/Shanghai', 'active', 'DEALER', 'SHARED',
   '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, now(), now(), now(), 0),
  ('22222222-0000-0000-0000-000000000002'::uuid, 'DEALER_A2', '经销商A2', '医疗器械', 'Asia/Shanghai', 'active', 'DEALER', 'SHARED',
   '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, now(), now(), now(), 0),
  ('22222222-0000-0000-0000-000000000003'::uuid, 'DEALER_B1', '经销商B1', '医疗器械', 'Asia/Shanghai', 'active', 'DEALER', 'SHARED',
   '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, now(), now(), now(), 0)
) AS v(id, code, name, industry, timezone, status, tenant_type, deployment_mode,
       modules_enabled, quota, attrs, enabled_at, created_at, updated_at, version)
WHERE NOT EXISTS (SELECT 1 FROM tenants t WHERE t.code = v.code);

UPDATE tenants SET owner_manufacturer_id = '11111111-0000-0000-0000-000000000001'
WHERE code IN ('DEALER_A1','DEALER_A2') AND owner_manufacturer_id IS NULL;
UPDATE tenants SET owner_manufacturer_id = '11111111-0000-0000-0000-000000000002'
WHERE code = 'DEALER_B1' AND owner_manufacturer_id IS NULL;

-- ========== 2. dealer 主数据（厂家侧） ==========
INSERT INTO dealers (tenant_id, code, name, level, status, created_at, updated_at, version)
SELECT t.id, v.code, v.name, 'VIP', 'active', now(), now(), 0
FROM (VALUES
  ('MFR_A','D-A1','经销商A1主数据'),
  ('MFR_A','D-A2','经销商A2主数据'),
  ('MFR_B','D-B1','经销商B1主数据')
) AS v(tenant_code, code, name)
JOIN tenants t ON t.code = v.tenant_code
WHERE NOT EXISTS (
  SELECT 1 FROM dealers d WHERE d.tenant_id = t.id AND d.code = v.code AND d.deleted_at IS NULL
);

-- ========== 3. 租户-dealer 绑定 ==========
INSERT INTO tenant_dealer_bindings (dealer_tenant_id, manufacturer_tenant_id, dealer_id, status, bound_at, created_at, updated_at, version)
SELECT dt.id, mt.id, d.id, 'active', now(), now(), now(), 0
FROM (VALUES
  ('DEALER_A1','MFR_A','D-A1'),
  ('DEALER_A2','MFR_A','D-A2'),
  ('DEALER_B1','MFR_B','D-B1')
) AS v(dt_code, mt_code, dealer_code)
JOIN tenants dt ON dt.code = v.dt_code
JOIN tenants mt ON mt.code = v.mt_code
JOIN dealers d ON d.tenant_id = mt.id AND d.code = v.dealer_code AND d.deleted_at IS NULL
WHERE NOT EXISTS (
  SELECT 1 FROM tenant_dealer_bindings b
  WHERE b.dealer_tenant_id = dt.id AND b.deleted_at IS NULL AND b.status = 'active'
);

-- ========== 4. 租户管理员账号 ==========
INSERT INTO users (tenant_id, username, name, user_type, role, password_hash, must_change_password,
                   password_updated_at, status, login_fail_count, attrs, created_at, updated_at, version)
SELECT t.id, v.username, v.name, 'tenant_admin', 'tenant_admin', '$2a$10$hfbr8i5pCRVv.11B2V.xC.b.1TkKfCL9bILnKwBkJg/OdJmYSP0N.', true, now(), 'active', 0, '{}'::jsonb, now(), now(), 0
FROM (VALUES
  ('MFR_A','mfr_a_admin','厂家A管理员'),
  ('MFR_B','mfr_b_admin','厂家B管理员'),
  ('DEALER_A1','dealer_a1_admin','经销商A1管理员'),
  ('DEALER_A2','dealer_a2_admin','经销商A2管理员'),
  ('DEALER_B1','dealer_b1_admin','经销商B1管理员')
) AS v(tenant_code, username, name)
JOIN tenants t ON t.code = v.tenant_code
WHERE NOT EXISTS (
  SELECT 1 FROM users u WHERE u.tenant_id = t.id AND u.username = v.username AND u.deleted_at IS NULL
);
-- 经销商租户管理员回填 dealer_id（与运行时开通逻辑一致，用于数据权限与角色判定）
UPDATE users u SET dealer_id = b.dealer_id
FROM tenant_dealer_bindings b
WHERE u.tenant_id = b.dealer_tenant_id
  AND u.user_type = 'tenant_admin'
  AND u.dealer_id IS NULL
  AND u.deleted_at IS NULL;

-- ========== 5. 默认角色/资源/策略（与 TenantRoleProvisioner 行为一致） ==========
-- 5.1 菜单资源：按租户类型从 platform_menus 生成
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at, version)
SELECT t.id,
       COALESCE(NULLIF(pm.permission_code, ''), pm.menu_key || ':menu'),
       pm.label, 'menu', ARRAY['view'], pm.route, 'active', now(), now(), 0
FROM tenants t
JOIN platform_menus pm ON pm.status = 'active' AND pm.visible = true
  AND (pm.tenant_type = 'ALL' OR pm.tenant_type = t.tenant_type)
WHERE t.code IN ('MFR_A','MFR_B','DEALER_A1','DEALER_A2','DEALER_B1')
  AND NOT EXISTS (
    SELECT 1 FROM resources r
    WHERE r.tenant_id = t.id
      AND r.code = COALESCE(NULLIF(pm.permission_code, ''), pm.menu_key || ':menu')
      AND r.deleted_at IS NULL
  );

-- 5.2 角色：按租户类型从 role_templates 复制
INSERT INTO roles (tenant_id, code, name, role_type, description, status, created_at, updated_at, version)
SELECT t.id, rt.code, rt.name, 'template', rt.description, 'active', now(), now(), 0
FROM tenants t
JOIN role_templates rt ON rt.status = 'active' AND rt.tenant_type = t.tenant_type
WHERE t.code IN ('MFR_A','MFR_B','DEALER_A1','DEALER_A2','DEALER_B1')
  AND NOT EXISTS (
    SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.code = rt.code AND r.deleted_at IS NULL
  );

-- 5.3 策略与资源绑定：每个角色一条同名策略，绑定该租户全部菜单资源
INSERT INTO strategies (tenant_id, name, description, status, created_at, updated_at, version)
SELECT r.tenant_id, r.name, r.description, 'active', now(), now(), 0
FROM roles r
JOIN tenants t ON t.id = r.tenant_id
WHERE t.code IN ('MFR_A','MFR_B','DEALER_A1','DEALER_A2','DEALER_B1')
  AND r.role_type = 'template'
  AND NOT EXISTS (
    SELECT 1 FROM strategies s WHERE s.tenant_id = r.tenant_id AND s.name = r.name AND s.deleted_at IS NULL
  );

INSERT INTO role_strategies (role_id, strategy_id, created_at)
SELECT r.id, s.id, now()
FROM roles r
JOIN strategies s ON s.tenant_id = r.tenant_id AND s.name = r.name AND s.deleted_at IS NULL
JOIN tenants t ON t.id = r.tenant_id
WHERE t.code IN ('MFR_A','MFR_B','DEALER_A1','DEALER_A2','DEALER_B1')
  AND r.role_type = 'template'
  AND NOT EXISTS (SELECT 1 FROM role_strategies rs WHERE rs.role_id = r.id AND rs.strategy_id = s.id);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT s.id, res.id, ARRAY['view'], now()
FROM strategies s
JOIN roles r ON r.tenant_id = s.tenant_id AND r.name = s.name AND r.role_type = 'template' AND r.deleted_at IS NULL
JOIN resources res ON res.tenant_id = s.tenant_id AND res.type = 'menu' AND res.deleted_at IS NULL
JOIN tenants t ON t.id = s.tenant_id
WHERE t.code IN ('MFR_A','MFR_B','DEALER_A1','DEALER_A2','DEALER_B1')
  AND NOT EXISTS (SELECT 1 FROM strategy_resources sr WHERE sr.strategy_id = s.id AND sr.resource_id = res.id);

-- 5.4 角色数据权限
INSERT INTO role_data_policies (role_id, data_scope, position_tree_enabled, self_created_enabled, config, updated_at)
SELECT r.id, rt.data_scope,
       (rt.data_scope = 'POSITION_TREE'),
       (rt.data_scope = 'SELF_CREATED'),
       '{}'::jsonb, now()
FROM roles r
JOIN role_templates rt ON rt.code = r.code
JOIN tenants t ON t.id = r.tenant_id AND rt.tenant_type = t.tenant_type
WHERE t.code IN ('MFR_A','MFR_B','DEALER_A1','DEALER_A2','DEALER_B1')
  AND r.role_type = 'template'
  AND NOT EXISTS (SELECT 1 FROM role_data_policies p WHERE p.role_id = r.id);

-- 5.5 租户管理员绑定 *_ADMIN 角色
INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT u.id, r.id, now()
FROM users u
JOIN roles r ON r.tenant_id = u.tenant_id AND r.code = (
  CASE WHEN u.user_type = 'tenant_admin' AND u.dealer_id IS NULL THEN 'MANUFACTURER_ADMIN'
       WHEN u.user_type = 'tenant_admin' AND u.dealer_id IS NOT NULL THEN 'DEALER_ADMIN' END)
WHERE u.user_type = 'tenant_admin' AND u.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);
-- ========== 6. 产品主数据 ==========
-- 6.1 厂家产品（厂家租户内）
INSERT INTO products (tenant_id, code, name_cn, unit, tax_rate, udi_required, safety_qty, status, created_at, updated_at, version)
SELECT t.id, v.code, v.name, '个', 0.13, false, 0, 'active', now(), now(), 0
FROM (VALUES
  ('MFR_A','MFR-P001','厂家A产品001'),
  ('MFR_A','MFR-P002','厂家A产品002'),
  ('MFR_A','MFR-P003','厂家A产品003'),
  ('MFR_A','MFR-P004','厂家A产品004'),
  ('MFR_A','MFR-P005','厂家A产品005'),
  ('MFR_B','MFRB-P001','厂家B产品001'),
  ('MFR_B','MFRB-P002','厂家B产品002')
) AS v(tenant_code, code, name)
JOIN tenants t ON t.code = v.tenant_code
WHERE NOT EXISTS (
  SELECT 1 FROM products p WHERE p.tenant_id = t.id AND p.code = v.code AND p.deleted_at IS NULL
);

-- 6.2 经销商自有产品（经销商租户内）
INSERT INTO products (tenant_id, code, name_cn, unit, tax_rate, udi_required, safety_qty, status, created_at, updated_at, version)
SELECT t.id, v.code, v.name, '个', 0.13, false, 0, 'active', now(), now(), 0
FROM (VALUES
  ('DEALER_A1','A1-P001','经销商A1产品001'),
  ('DEALER_A1','A1-P002','经销商A1产品002'),
  ('DEALER_A2','A2-P001','经销商A2产品001'),
  ('DEALER_A2','A2-P999','经销商A2产品999(停用对码)'),
  ('DEALER_B1','B1-P001','经销商B1产品001')
) AS v(tenant_code, code, name)
JOIN tenants t ON t.code = v.tenant_code
WHERE NOT EXISTS (
  SELECT 1 FROM products p WHERE p.tenant_id = t.id AND p.code = v.code AND p.deleted_at IS NULL
);

-- ========== 7. 产品对码 ==========
INSERT INTO product_mappings (manufacturer_tenant_id, dealer_tenant_id, manufacturer_product_id, dealer_product_id,
                              manufacturer_product_code, dealer_product_code, package_unit, conversion_rate, status, created_at, updated_at, version)
SELECT mt.id, dt.id, mp.id, dp.id, v.mp_code, v.dp_code, 'box', 1, v.status, now(), now(), 0
FROM (VALUES
  ('MFR_A','DEALER_A1','MFR-P001','A1-P001','active'),
  ('MFR_A','DEALER_A1','MFR-P002','A1-P002','active'),
  ('MFR_A','DEALER_A2','MFR-P003','A2-P001','active'),
  ('MFR_A','DEALER_A2','MFR-P004','A2-P999','inactive'),
  ('MFR_B','DEALER_B1','MFRB-P001','B1-P001','active')
) AS v(mt_code, dt_code, mp_code, dp_code, status)
JOIN tenants mt ON mt.code = v.mt_code
JOIN tenants dt ON dt.code = v.dt_code
JOIN products mp ON mp.tenant_id = mt.id AND mp.code = v.mp_code AND mp.deleted_at IS NULL
JOIN products dp ON dp.tenant_id = dt.id AND dp.code = v.dp_code AND dp.deleted_at IS NULL
WHERE NOT EXISTS (
  SELECT 1 FROM product_mappings pm
  WHERE pm.manufacturer_tenant_id = mt.id AND pm.dealer_tenant_id = dt.id
    AND pm.manufacturer_product_id = mp.id AND pm.deleted_at IS NULL
);