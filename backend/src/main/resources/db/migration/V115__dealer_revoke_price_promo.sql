-- V115: 演示前 RBAC 收尾
-- 1. 经销商类角色（DEALER_ADMIN/SERVICE/SALES）彻底回收促销与产品价格的查看/编辑资源与 API 资源
--    （V114 只回收了写权限；价格/促销属厂家维护，经销商侧不应可见）。
-- 2. 数据范围由后端 DataScope 在代码层处理（经销商仅自己，后台角色未映射时回退本租户全量）。

DELETE FROM strategy_resources
WHERE strategy_id IN (
    SELECT rs.strategy_id
    FROM role_strategies rs
    JOIN roles r ON r.id = rs.role_id
    WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
      AND r.deleted_at IS NULL
      AND r.code IN ('DEALER_ADMIN','DEALER_SERVICE','DEALER_SALES')
)
AND resource_id IN (
    SELECT id FROM resources
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
      AND deleted_at IS NULL
      AND ( code LIKE 'promotion:%' OR code LIKE 'product_price:%'
         OR code IN ('api.promotion','api.product_price')
         OR code LIKE 'api.promotion.%' OR code LIKE 'api.product_price.%' )
);