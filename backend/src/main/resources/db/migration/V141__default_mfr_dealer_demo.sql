-- V141: 默认厂家租户(default/sys_admin)的跨租户对码演示数据
-- 背景：v4.5.0 跨租户协同 demo 起初只在 MFR_A 厂家租户下建了经销商(DEALER_A1/A2)与产品对码，
--       而需求第1条定义的"厂家环境"是 default 租户(sys_admin 登录)。default 没有归属它的
--       经销商租户，也没有任何 product_mappings，导致 sys_admin 打开"产品对码"页为空。
-- 本迁移：为 default 厂家补齐一个归属它的下游经销商租户 DEALER_D1（含主数据/绑定/管理员/
--         RBAC/产品），并建立 default 厂家产品 <-> DEALER_D1 经销商产品的对码，
--         使 sys_admin 在 default 租户即可在"产品对码"页看到数据。
-- 幂等：WHERE NOT EXISTS，可重复执行。
-- 说明：经销商管理员账号初始密码 Sh123456（与 V52 同一 bcrypt），首次登录强制改密。

-- ========== 1. 经销商租户（归属 default 厂家） ==========
INSERT INTO tenants (id, code, name, industry, timezone, status, tenant_type, deployment_mode,
                     owner_manufacturer_id, modules_enabled, quota, attrs, enabled_at, created_at, updated_at, version)
SELECT v.* FROM (VALUES
  ('22222222-1111-0000-0000-000000000001'::uuid, 'DEALER_D1', '默认厂家经销商', '医疗器械',
   'Asia/Shanghai', 'active', 'DEALER', 'SHARED',
   '11111111-1111-1111-1111-111111111111'::uuid,
   '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, now(), now(), now(), 0)
) AS v(id, code, name, industry, timezone, status, tenant_type, deployment_mode,
       owner_manufacturer_id, modules_enabled, quota, attrs, enabled_at, created_at, updated_at, version)
WHERE NOT EXISTS (SELECT 1 FROM tenants t WHERE t.code = 'DEALER_D1');

-- ========== 2. dealer 主数据（default 厂家侧） ==========
INSERT INTO dealers (tenant_id, code, name, level, status, created_at, updated_at, version)
SELECT t.id, 'D-D1', '默认厂家经销商', 'VIP', 'active', now(), now(), 0
FROM tenants t
WHERE t.code = 'default'
  AND NOT EXISTS (
    SELECT 1 FROM dealers d WHERE d.tenant_id = t.id AND d.code = 'D-D1' AND d.deleted_at IS NULL
  );

-- ========== 3. 租户-dealer 绑定 ==========
INSERT INTO tenant_dealer_bindings (dealer_tenant_id, manufacturer_tenant_id, dealer_id, status, bound_at, created_at, updated_at, version)
SELECT dt.id, mt.id, d.id, 'active', now(), now(), now(), 0
FROM tenants dt
JOIN tenants mt ON mt.code = 'default'
JOIN dealers d ON d.tenant_id = mt.id AND d.code = 'D-D1' AND d.deleted_at IS NULL
WHERE dt.code = 'DEALER_D1'
  AND NOT EXISTS (
    SELECT 1 FROM tenant_dealer_bindings b
    WHERE b.dealer_tenant_id = dt.id AND b.deleted_at IS NULL AND b.status = 'active'
  );

-- ========== 4. 经销商租户管理员账号 ==========
INSERT INTO users (tenant_id, username, name, user_type, role, password_hash, must_change_password,
                   password_updated_at, status, login_fail_count, attrs, created_at, updated_at, version)
SELECT t.id, 'dealer_d1_admin', '默认厂家经销商管理员', 'tenant_admin', 'tenant_admin',
       '$2a$10$hfbr8i5pCRVv.11B2V.xC.b.1TkKfCL9bILnKwBkJg/OdJmYSP0N.', true, now(), 'active', 0,
       '{}'::jsonb, now(), now(), 0
FROM tenants t
WHERE t.code = 'DEALER_D1'
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.tenant_id = t.id AND u.username = 'dealer_d1_admin' AND u.deleted_at IS NULL
  );

UPDATE users u SET dealer_id = b.dealer_id
FROM tenant_dealer_bindings b
WHERE u.tenant_id = b.dealer_tenant_id
  AND u.user_type = 'tenant_admin'
  AND u.dealer_id IS NULL
  AND u.deleted_at IS NULL;

-- ========== 5. 默认角色/资源/策略（与 TenantRoleProvisioner 行为一致） ==========
-- 5.1 菜单资源：按 DEALER 租户类型从 platform_menus 生成
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at, version)
SELECT t.id,
       COALESCE(NULLIF(pm.permission_code, ''), pm.menu_key || ':menu'),
       pm.label, 'menu', ARRAY['view'], pm.route, 'active', now(), now(), 0
FROM tenants t
JOIN platform_menus pm ON pm.status = 'active' AND pm.visible = true
  AND (pm.tenant_type = 'ALL' OR pm.tenant_type = t.tenant_type)
WHERE t.code = 'DEALER_D1'
  AND NOT EXISTS (
    SELECT 1 FROM resources r
    WHERE r.tenant_id = t.id
      AND r.code = COALESCE(NULLIF(pm.permission_code, ''), pm.menu_key || ':menu')
      AND r.deleted_at IS NULL
  );

-- 5.2 角色：按 DEALER 类型从 role_templates 复制
INSERT INTO roles (tenant_id, code, name, role_type, description, status, created_at, updated_at, version)
SELECT t.id, rt.code, rt.name, 'template', rt.description, 'active', now(), now(), 0
FROM tenants t
JOIN role_templates rt ON rt.status = 'active' AND rt.tenant_type = t.tenant_type
WHERE t.code = 'DEALER_D1'
  AND NOT EXISTS (
    SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.code = rt.code AND r.deleted_at IS NULL
  );

-- 5.3 策略与资源绑定
INSERT INTO strategies (tenant_id, name, description, status, created_at, updated_at, version)
SELECT r.tenant_id, r.name, r.description, 'active', now(), now(), 0
FROM roles r
JOIN tenants t ON t.id = r.tenant_id
WHERE t.code = 'DEALER_D1'
  AND r.role_type = 'template'
  AND NOT EXISTS (
    SELECT 1 FROM strategies s WHERE s.tenant_id = r.tenant_id AND s.name = r.name AND s.deleted_at IS NULL
  );

INSERT INTO role_strategies (role_id, strategy_id, created_at)
SELECT r.id, s.id, now()
FROM roles r
JOIN strategies s ON s.tenant_id = r.tenant_id AND s.name = r.name AND s.deleted_at IS NULL
JOIN tenants t ON t.id = r.tenant_id
WHERE t.code = 'DEALER_D1'
  AND r.role_type = 'template'
  AND NOT EXISTS (SELECT 1 FROM role_strategies rs WHERE rs.role_id = r.id AND rs.strategy_id = s.id);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT s.id, res.id, ARRAY['view'], now()
FROM strategies s
JOIN roles r ON r.tenant_id = s.tenant_id AND r.name = s.name AND r.role_type = 'template' AND r.deleted_at IS NULL
JOIN resources res ON res.tenant_id = s.tenant_id AND res.type = 'menu' AND res.deleted_at IS NULL
JOIN tenants t ON t.id = s.tenant_id
WHERE t.code = 'DEALER_D1'
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
WHERE t.code = 'DEALER_D1'
  AND r.role_type = 'template'
  AND NOT EXISTS (SELECT 1 FROM role_data_policies p WHERE p.role_id = r.id);

-- 5.5 经销商管理员绑定 DEALER_ADMIN 角色
INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT u.id, r.id, now()
FROM users u
JOIN roles r ON r.tenant_id = u.tenant_id AND r.code = 'DEALER_ADMIN'
WHERE u.user_type = 'tenant_admin' AND u.dealer_id IS NOT NULL AND u.deleted_at IS NULL
  AND u.tenant_id = (SELECT id FROM tenants WHERE code = 'DEALER_D1')
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- ========== 6. 经销商租户产品（DEALER_D1 自有编码） ==========
INSERT INTO products (tenant_id, code, name_cn, unit, tax_rate, udi_required, safety_qty, status, created_at, updated_at, version)
SELECT t.id, v.code, v.name, '个', 0.13, false, 0, 'active', now(), now(), 0
FROM (VALUES
  ('DD1-P001', '经销商D1-锁定接骨板'),
  ('DD1-P002', '经销商D1-锁定接骨螺钉'),
  ('DD1-P003', '经销商D1-股骨髓内钉'),
  ('DD1-P004', '经销商D1-脊柱椎弓根螺钉'),
  ('DD1-P005', '经销商D1-椎间融合器(停用对码)')
) AS v(code, name)
JOIN tenants t ON t.code = 'DEALER_D1'
WHERE NOT EXISTS (
  SELECT 1 FROM products p WHERE p.tenant_id = t.id AND p.code = v.code AND p.deleted_at IS NULL
);

-- ========== 7. 产品对码（default 厂家产品 <-> DEALER_D1 经销商产品） ==========
INSERT INTO product_mappings (manufacturer_tenant_id, dealer_tenant_id, manufacturer_product_id, dealer_product_id,
                              manufacturer_product_code, dealer_product_code, package_unit, conversion_rate, status,
                              remark, created_at, updated_at, version)
SELECT mt.id, dt.id, mp.id, dp.id, v.mp_code, v.dp_code, 'box', 1, v.status, 'V141 default厂家对码演示',
       now(), now(), 0
FROM (VALUES
  ('PRD-T001', 'DD1-P001', 'active'),
  ('PRD-T003', 'DD1-P002', 'active'),
  ('PRD-T005', 'DD1-P003', 'active'),
  ('PRD-S001', 'DD1-P004', 'active'),
  ('PRD-S002', 'DD1-P005', 'disabled')
) AS v(mp_code, dp_code, status)
JOIN tenants mt ON mt.code = 'default'
JOIN tenants dt ON dt.code = 'DEALER_D1'
JOIN products mp ON mp.tenant_id = mt.id AND mp.code = v.mp_code AND mp.deleted_at IS NULL
JOIN products dp ON dp.tenant_id = dt.id AND dp.code = v.dp_code AND dp.deleted_at IS NULL
WHERE NOT EXISTS (
  SELECT 1 FROM product_mappings pm
  WHERE pm.manufacturer_tenant_id = mt.id AND pm.dealer_tenant_id = dt.id
    AND pm.manufacturer_product_id = mp.id AND pm.deleted_at IS NULL
);
