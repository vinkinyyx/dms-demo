-- v4.3.0：注册新功能权限资源并授给超管策略(id=1)与厂家业务角色。
-- 幂等：ON CONFLICT DO NOTHING / NOT EXISTS。

-- 1) 注册按钮级权限资源（缺则建）
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at, version)
SELECT DISTINCT ten.tenant_id, t.code, t.name, 'button',
       ARRAY['view','create','edit','delete','search','manage','approve']::varchar[],
       NULL, 'active', now(), now(), 0
FROM (
  VALUES
    ('product_global_discount:view','产品全局折扣-查看'),
    ('product_global_discount:search','产品全局折扣-查询'),
    ('product_global_discount:create','产品全局折扣-新建'),
    ('product_global_discount:edit','产品全局折扣-编辑'),
    ('product_global_discount:delete','产品全局折扣-删除'),
    ('dealer_global_discount:view','客户全局折扣-查看'),
    ('dealer_global_discount:search','客户全局折扣-查询'),
    ('dealer_global_discount:create','客户全局折扣-新建'),
    ('dealer_global_discount:edit','客户全局折扣-编辑'),
    ('dealer_global_discount:delete','客户全局折扣-删除'),
    ('customer_voucher:view','代金券-查看'),
    ('customer_voucher:manage','代金券-管理'),
    ('customer_registration:view','客户注册审核-查看'),
    ('customer_registration:approve','客户注册审核-审批'),
    ('dealer_contact:view','客户联系人-查看'),
    ('dealer_contact:create','客户联系人-新建'),
    ('dealer_contact:edit','客户联系人-编辑'),
    ('dealer_contact:delete','客户联系人-删除'),
    ('dealer_address:view','客户地址-查看'),
    ('dealer_address:create','客户地址-新建'),
    ('dealer_address:edit','客户地址-编辑'),
    ('dealer_address:delete','客户地址-删除'),
    ('contract_price:view','合同价-查看'),
    ('contract_price:create','合同价-新建'),
    ('contract_price:edit','合同价-编辑'),
    ('contract_price:delete','合同价-删除')
) AS t(code, name)
CROSS JOIN (SELECT '11111111-1111-1111-1111-111111111111'::uuid AS tenant_id) ten
WHERE NOT EXISTS (
  SELECT 1 FROM resources r
  WHERE r.tenant_id = ten.tenant_id AND r.code = t.code AND r.deleted_at IS NULL
);

-- 2) 授给超管策略(id=1) 全部新权限
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT 1, r.id,
       ARRAY['view','create','edit','delete','search','manage','approve']::varchar[], now()
FROM resources r
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'::uuid
  AND r.deleted_at IS NULL
  AND r.code IN (
    'product_global_discount:view','product_global_discount:search','product_global_discount:create','product_global_discount:edit','product_global_discount:delete',
    'dealer_global_discount:view','dealer_global_discount:search','dealer_global_discount:create','dealer_global_discount:edit','dealer_global_discount:delete',
    'customer_voucher:view','customer_voucher:manage',
    'customer_registration:view','customer_registration:approve',
    'dealer_contact:view','dealer_contact:create','dealer_contact:edit','dealer_contact:delete',
    'dealer_address:view','dealer_address:create','dealer_address:edit','dealer_address:delete',
    'contract_price:view','contract_price:create','contract_price:edit','contract_price:delete'
  )
  AND NOT EXISTS (
    SELECT 1 FROM strategy_resources sr WHERE sr.strategy_id = 1 AND sr.resource_id = r.id
  );

-- 3) 授给厂家业务角色（非经销商）：折扣/券/联系人/地址/合同价/注册审核查看
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, res.id, ARRAY['view','search','create','edit','delete','manage','approve']::varchar[], now()
FROM role_strategies rs
JOIN roles rl ON rl.id = rs.role_id
JOIN resources res ON res.tenant_id = rl.tenant_id AND res.deleted_at IS NULL
WHERE rl.tenant_id = '11111111-1111-1111-1111-111111111111'::uuid
  AND rl.deleted_at IS NULL
  AND COALESCE(rl.code,'') NOT IN ('SYS_ADMIN','MANUFACTURER_ADMIN','MFR_ADMIN','TENANT_ADMIN','DEALER_ADMIN','DEALER_SERVICE','DEALER_SALES','CUSTOMER')
  AND rl.code NOT LIKE 'AUTO-ROLE-%'
  AND res.code IN (
    'product_global_discount:view','product_global_discount:search','product_global_discount:create','product_global_discount:edit','product_global_discount:delete',
    'dealer_global_discount:view','dealer_global_discount:search','dealer_global_discount:create','dealer_global_discount:edit','dealer_global_discount:delete',
    'customer_voucher:view','customer_voucher:manage',
    'customer_registration:view','customer_registration:approve',
    'dealer_contact:view','dealer_contact:create','dealer_contact:edit',
    'dealer_address:view','dealer_address:create','dealer_address:edit',
    'contract_price:view','contract_price:create','contract_price:edit'
  )
ON CONFLICT (strategy_id, resource_id) DO NOTHING;
