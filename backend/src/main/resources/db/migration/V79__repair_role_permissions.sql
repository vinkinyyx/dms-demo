-- Repair seeded role permissions for v3.11 demo/test tenants.
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at, version)
SELECT r.tenant_id,
       x.code,
       x.name,
       'api',
       ARRAY['view','create','update','delete','export','import']::varchar[],
       x.path,
       'active', now(), now(), 0
FROM roles r
CROSS JOIN (VALUES
  ('product_mapping:view','产品对码查看','/api/product-mappings/**'),
  ('product_mapping:search','产品对码查询','/api/product-mappings/**'),
  ('product_mapping:import','产品对码导入','/api/product-mappings/import/**'),
  ('product_mapping:export','产品对码导出','/api/product-mappings/**')
) AS x(code,name,path)
LEFT JOIN resources res ON res.tenant_id=r.tenant_id AND res.code=x.code AND res.deleted_at IS NULL
WHERE r.deleted_at IS NULL AND res.id IS NULL;

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT s.id, res.id, ARRAY['view','create','update','delete','export','import']::varchar[], now()
FROM strategies s
JOIN roles r ON r.tenant_id=s.tenant_id AND r.name=s.name AND r.deleted_at IS NULL
JOIN resources res ON res.tenant_id=s.tenant_id AND res.deleted_at IS NULL
WHERE s.deleted_at IS NULL
  AND res.code LIKE 'product_mapping:%'
  AND r.code IN ('TENANT_ADMIN','MFR_ADMIN','MANUFACTURER_ADMIN','SYS_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM strategy_resources sr WHERE sr.strategy_id=s.id AND sr.resource_id=res.id);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT s.id, res.id, res.operations, now()
FROM strategies s
JOIN roles r ON r.tenant_id=s.tenant_id AND r.name=s.name AND r.deleted_at IS NULL
JOIN tenants t ON t.id=r.tenant_id
JOIN resources res ON res.tenant_id=s.tenant_id AND res.deleted_at IS NULL
WHERE s.deleted_at IS NULL AND t.tenant_type='MANUFACTURER'
  AND r.code IN ('TENANT_ADMIN','MFR_ADMIN','MANUFACTURER_ADMIN')
  AND res.code LIKE 'product_mapping:%'
  AND NOT EXISTS (SELECT 1 FROM strategy_resources sr WHERE sr.strategy_id=s.id AND sr.resource_id=res.id);

INSERT INTO role_strategies (role_id, strategy_id, created_at)
SELECT r.id, s.id, now()
FROM roles r
JOIN strategies s ON s.tenant_id=r.tenant_id
WHERE r.tenant_id='11111111-1111-1111-1111-111111111111'
  AND r.code<>'SYS_ADMIN' AND r.deleted_at IS NULL AND s.deleted_at IS NULL
  AND s.name IN ('销售管理策略','报表查看策略','客服策略','商务策略','财务策略','合同专员策略','经销商基础策略')
  AND NOT EXISTS (SELECT 1 FROM role_strategies rs WHERE rs.role_id=r.id AND rs.strategy_id=s.id);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT s.id, res.id, ARRAY['view','create','update','delete','export','import']::varchar[], now()
FROM strategies s
JOIN resources res ON res.tenant_id=s.tenant_id AND res.deleted_at IS NULL
WHERE s.tenant_id='11111111-1111-1111-1111-111111111111' AND s.deleted_at IS NULL
  AND s.name IN ('销售管理策略','报表查看策略','客服策略','商务策略','财务策略','合同专员策略','经销商基础策略')
  AND (res.code LIKE 'report_%' OR res.code LIKE 'api.%' OR res.code LIKE 'menu.%' OR res.code LIKE 'product_mapping:%')
  AND NOT EXISTS (SELECT 1 FROM strategy_resources sr WHERE sr.strategy_id=s.id AND sr.resource_id=res.id);
