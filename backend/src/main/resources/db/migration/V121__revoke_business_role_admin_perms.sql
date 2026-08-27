-- V120: 彻底回收业务角色被误授的管理员级权限
-- 根因：V79/V81 种子数据及 V114 的"幂等补齐菜单"把 menu.* 资源（menu.user/menu.role/menu.tenant
--       等）重新绑回所有业务策略；V114 的首个 DELETE 未覆盖 menu.*，导致非管理员角色长期持有
--       approval:admin、approval:manage、menu.user、menu.role、menu.tenant、tenant_ui_config:view、
--       auth:* 等管理员级权限码，业务角色可访问 /api/approval/admin/instances 并看到用户/角色/租户菜单。
--
-- 本迁移：
--   1. 对 default 租户下所有非管理员角色，回收管理员级资源绑定（含 menu.* 与 api.* 派生资源）。
--   2. 对经销商类角色额外回收 promotion/product_price 的全部查看与搜索权限（含 api.* 资源）。
--   3. 业务角色（SALES/CS/BIZ/FIN/CONTRACT_SPEC）保留 promotion:view/search、product_price:view/search
--      （下单计价需要，见 V116/V117），不在本次回收范围。
--
-- 幂等：DELETE 天然幂等，可安全重复执行。

-- ===== 1. 回收非管理员角色的管理员级资源 =====
DELETE FROM strategy_resources
WHERE strategy_id IN (
    SELECT rs.strategy_id
    FROM role_strategies rs
    JOIN roles r ON r.id = rs.role_id
    WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
      AND r.deleted_at IS NULL
      AND COALESCE(r.code, '') NOT IN ('SYS_ADMIN', 'MANUFACTURER_ADMIN', 'MFR_ADMIN', 'TENANT_ADMIN')
)
AND resource_id IN (
    SELECT id FROM resources
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
      AND deleted_at IS NULL
      AND (
            code IN (
                'approval:admin','approval:manage',
                'auth:create','auth:edit','auth:delete',
                'user:create','user:edit','user:delete','user:reset_password','user:unlock',
                'role:create','role:edit','role:delete','role:assign',
                'menu.user','menu.role','menu.tenant','menu.platform','menu.account',
                'tenant_ui_config:view','tenant_ui_config:edit',
                'api.user','api.role','api.tenant','api.tenant_user'
            )
         OR code LIKE 'api.user.%'
         OR code LIKE 'api.role.%'
         OR code LIKE 'api.tenant.%'
      )
);

-- ===== 2. 经销商类角色额外回收促销与产品价格的全部权限（查看/搜索/编辑 + api.* 资源） =====
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
      AND ( code LIKE 'promotion:%'
         OR code LIKE 'product_price:%'
         OR code IN ('api.promotion','api.product_price')
         OR code LIKE 'api.promotion.%'
         OR code LIKE 'api.product_price.%' )
);