-- V116: 修复演示前 RBAC 权限码不匹配导致业务角色无法查看产品/促销
-- 根因：V114 幂等补齐菜单时写入了错误的资源码 products:view（带 s），
--       而 ProductController 的 @PreAuthorize 要求 product:view（不带 s）。
--       同时 V114 过度回收了非管理员角色的 promotion:view/promotion:search，
--       导致销售/客服/商务/财务/合同无法查看促销规则（下单计价需要）。

-- 1. 给所有非管理员业务角色补授 product:view / product:search（控制器实际要求的码）
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, res.id, ARRAY['view','search'], now()
FROM role_strategies rs
JOIN roles r ON r.id = rs.role_id
CROSS JOIN resources res
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND r.deleted_at IS NULL
  AND res.tenant_id = r.tenant_id
  AND res.deleted_at IS NULL
  AND res.code IN ('product:view','product:search')
  AND COALESCE(r.code, '') NOT IN ('SYS_ADMIN','MANUFACTURER_ADMIN','MFR_ADMIN','TENANT_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM strategy_resources x
    WHERE x.strategy_id = rs.strategy_id AND x.resource_id = res.id
  );

-- 2. 给需要查看促销的业务角色补授 promotion:view / promotion:search
--    销售经理/销售/客服/商务/财务/合同需要查看促销以了解计价规则；经销商不补（促销是厂家内部策略）
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, res.id, ARRAY['view','search'], now()
FROM role_strategies rs
JOIN roles r ON r.id = rs.role_id
CROSS JOIN resources res
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND r.deleted_at IS NULL
  AND res.tenant_id = r.tenant_id
  AND res.deleted_at IS NULL
  AND res.code IN ('promotion:view','promotion:search')
  AND COALESCE(r.code, '') IN ('SALES_MGR','SALES','CS','BIZ','FIN','CONTRACT_SPEC')
  AND NOT EXISTS (
    SELECT 1 FROM strategy_resources x
    WHERE x.strategy_id = rs.strategy_id AND x.resource_id = res.id
  );

-- 3. 经销商角色彻底不保留 product_price 任何权限（view/search 也回收），防止价格泄露
DELETE FROM strategy_resources
WHERE strategy_id IN (
    SELECT rs.strategy_id FROM role_strategies rs
    JOIN roles r ON r.id = rs.role_id
    WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
      AND r.deleted_at IS NULL
      AND r.code IN ('DEALER_ADMIN','DEALER_SERVICE','DEALER_SALES')
)
AND resource_id IN (
    SELECT id FROM resources
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
      AND deleted_at IS NULL
      AND (code LIKE 'product_price:%' OR code = 'api.product_price' OR code LIKE 'api.product_price.%')
);
