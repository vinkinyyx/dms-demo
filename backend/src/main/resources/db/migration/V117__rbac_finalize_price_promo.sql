-- V117: 收尾 RBAC
-- 1. 给业务角色补 product_price:view/search（下单计价需要查看价格）
-- 2. 经销商角色再次确保回收 promotion 全部权限（V115 可能有残留 strategy 绑定）

-- 1. 业务角色需要查看产品价格
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, res.id, ARRAY['view','search'], now()
FROM role_strategies rs
JOIN roles r ON r.id = rs.role_id
CROSS JOIN resources res
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND r.deleted_at IS NULL
  AND res.tenant_id = r.tenant_id
  AND res.deleted_at IS NULL
  AND res.code IN ('product_price:view','product_price:search')
  AND COALESCE(r.code, '') IN ('SALES_MGR','SALES','CS','BIZ','FIN','CONTRACT_SPEC')
  AND NOT EXISTS (
    SELECT 1 FROM strategy_resources x
    WHERE x.strategy_id = rs.strategy_id AND x.resource_id = res.id
  );

-- 2. 经销商角色彻底回收促销所有权限（含 view/search/create/edit/delete/api）
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
      AND (code LIKE 'promotion:%' OR code = 'api.promotion' OR code LIKE 'api.promotion.%')
);
