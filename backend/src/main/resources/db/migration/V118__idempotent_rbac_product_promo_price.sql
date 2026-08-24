-- V118: 幂等修复业务角色的产品/促销/价格查看权限
-- 根因：V116/V117 的 CROSS JOIN + NOT EXISTS 在某些执行计划下未正确插入。
-- 本迁移使用更直接的 JOIN 方式，确保业务角色获得 product/promotion/product_price 的 view/search 权限。
-- 注意：经销商类角色在代码层通过 PermissionChecker.isDealer() 拒绝访问价格/促销，
--       无需在数据库层面区分（因为多个角色共享同一组策略）。

-- 1. 确保所有业务策略（非管理员）拥有 product:view / product:search
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, pr.id, ARRAY['view','search'], now()
FROM role_strategies rs
CROSS JOIN resources pr
WHERE pr.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND pr.deleted_at IS NULL
  AND pr.code IN ('product:view','product:search')
  AND rs.strategy_id IN (
      SELECT rs2.strategy_id FROM role_strategies rs2
      JOIN roles r2 ON r2.id = rs2.role_id
      WHERE r2.tenant_id = '11111111-1111-1111-1111-111111111111' AND r2.deleted_at IS NULL
  )
  AND NOT EXISTS (
      SELECT 1 FROM strategy_resources x WHERE x.strategy_id = rs.strategy_id AND x.resource_id = pr.id
  );

-- 2. 确保所有业务策略拥有 promotion:view / promotion:search
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, pr.id, ARRAY['view','search'], now()
FROM role_strategies rs
CROSS JOIN resources pr
WHERE pr.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND pr.deleted_at IS NULL
  AND pr.code IN ('promotion:view','promotion:search')
  AND rs.strategy_id IN (
      SELECT rs2.strategy_id FROM role_strategies rs2
      JOIN roles r2 ON r2.id = rs2.role_id
      WHERE r2.tenant_id = '11111111-1111-1111-1111-111111111111' AND r2.deleted_at IS NULL
  )
  AND NOT EXISTS (
      SELECT 1 FROM strategy_resources x WHERE x.strategy_id = rs.strategy_id AND x.resource_id = pr.id
  );

-- 3. 确保所有业务策略拥有 product_price:view / product_price:search
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, pr.id, ARRAY['view','search'], now()
FROM role_strategies rs
CROSS JOIN resources pr
WHERE pr.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND pr.deleted_at IS NULL
  AND pr.code IN ('product_price:view','product_price:search')
  AND rs.strategy_id IN (
      SELECT rs2.strategy_id FROM role_strategies rs2
      JOIN roles r2 ON r2.id = rs2.role_id
      WHERE r2.tenant_id = '11111111-1111-1111-1111-111111111111' AND r2.deleted_at IS NULL
  )
  AND NOT EXISTS (
      SELECT 1 FROM strategy_resources x WHERE x.strategy_id = rs.strategy_id AND x.resource_id = pr.id
  );
