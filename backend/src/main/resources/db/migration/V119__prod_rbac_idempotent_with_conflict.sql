-- V119: 生产环境幂等补授 RBAC 权限（带 ON CONFLICT 避免主键冲突）
-- 与 V118 目标相同，但使用 ON CONFLICT DO NOTHING 处理已存在的策略资源绑定，
-- 避免生产数据库因历史数据差异导致 NOT EXISTS 竞态失败。

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, pr.id, ARRAY['view','search'], now()
FROM role_strategies rs
CROSS JOIN resources pr
WHERE pr.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND pr.deleted_at IS NULL
  AND pr.code IN ('product:view','product:search','promotion:view','promotion:search','product_price:view','product_price:search')
  AND rs.strategy_id IN (
      SELECT rs2.strategy_id FROM role_strategies rs2
      JOIN roles r2 ON r2.id = rs2.role_id
      WHERE r2.tenant_id = '11111111-1111-1111-1111-111111111111' AND r2.deleted_at IS NULL
  )
ON CONFLICT (strategy_id, resource_id) DO NOTHING;
