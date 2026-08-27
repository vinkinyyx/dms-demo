-- V122: 给非经销商业务角色补授产品/促销/价格查看权限（下单计价需要）
-- 根因：V116/V117/V118 的 CROSS JOIN + NOT EXISTS 在部分环境未正确插入，
--       导致 SALES/CS/BIZ/FIN/CONTRACT_SPEC 无法查看促销规则和产品价格（API 403）。
-- 经销商类角色（DEALER_*）不补，价格/促销属厂家内部数据。
-- 幂等：ON CONFLICT DO NOTHING。

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, res.id, ARRAY['view','search']::varchar[], now()
FROM role_strategies rs
JOIN roles r ON r.id = rs.role_id
JOIN resources res ON res.tenant_id = r.tenant_id
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND r.deleted_at IS NULL
  AND res.deleted_at IS NULL
  AND res.code IN ('promotion:view','promotion:search','product_price:view','product_price:search','product:view','product:search')
  AND COALESCE(r.code, '') NOT IN ('SYS_ADMIN','MANUFACTURER_ADMIN','MFR_ADMIN','TENANT_ADMIN','DEALER_ADMIN','DEALER_SERVICE','DEALER_SALES')
  AND r.code NOT LIKE 'AUTO-ROLE-%'
ON CONFLICT (strategy_id, resource_id) DO NOTHING;