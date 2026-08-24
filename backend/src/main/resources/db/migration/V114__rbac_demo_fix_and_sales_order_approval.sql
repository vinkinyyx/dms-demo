-- V114: 修复演示前 RBAC 审计发现的权限问题
-- 1. 回收非管理员角色被误授的高危资源（账号/角色/价格/促销/审批管理/租户配置）
--    根因：V7/V79 曾把全部 api.* 资源绑给所有策略，PermissionQueryService 会从
--    /api/users/**、/api/roles/** 等路径派生出 user:create、role:assign 等写权限。
-- 2. 新增 SALES_ORDER 销售订单审批模板（此前缺失导致销售订单提交即自动通过）。
--
-- 说明：以角色 code 判定管理员，管理员角色（SYS_ADMIN/MANUFACTURER_ADMIN）保留全部权限；
--       其余角色统一回收高危资源，不依赖 strategy.name 与 role.name 是否一致。

-- ===== 1. 回收非管理员策略上的高危资源 =====
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
                'user:create','user:edit','user:delete','user:reset_password','user:unlock',
                'role:create','role:edit','role:delete','role:assign',
                'auth:create','auth:edit','auth:delete',
                'product_price:create','product_price:edit','product_price:delete',
                'promotion:create','promotion:edit','promotion:delete',
                'approval:admin','approval:manage',
                'tenant_ui_config:view','tenant_ui_config:edit',
                'dealer:create','dealer:delete','dealer:edit'
            )
         OR code IN ('api.user','api.role','api.tenant','api.tenant_user','api.product_price','api.promotion')
         OR code LIKE 'api.user.%' OR code LIKE 'api.role.%' OR code LIKE 'api.tenant.%'
         OR code LIKE 'api.product_price.%' OR code LIKE 'api.promotion.%'
      )
);

-- 经销商类角色（DEALER_ADMIN/DEALER_SERVICE/DEALER_SALES）额外回收促销与产品价格的查看与编辑
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
      AND (code LIKE 'promotion:%' OR code LIKE 'product_price:%')
);

-- 幂等补齐：确保非管理员角色仍可访问其菜单（避免回收过当导致白屏）
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT rs.strategy_id, res.id, ARRAY['view','search'], now()
FROM role_strategies rs
JOIN roles r ON r.id = rs.role_id
CROSS JOIN resources res
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND r.deleted_at IS NULL
  AND res.tenant_id = r.tenant_id
  AND res.deleted_at IS NULL
  AND res.type = 'menu'
  AND COALESCE(r.code, '') NOT IN ('SYS_ADMIN','MANUFACTURER_ADMIN','MFR_ADMIN','TENANT_ADMIN')
  AND res.code NOT IN ('menu.user','menu.role','menu.account','menu.tenant','menu.platform')
  AND NOT EXISTS (SELECT 1 FROM strategy_resources x WHERE x.strategy_id = rs.strategy_id AND x.resource_id = res.id);

-- ===== 2. 销售订单审批模板 =====
DO $$
DECLARE
    v_tid UUID := '11111111-1111-1111-1111-111111111111';
    v_tpl BIGINT;
    v_node BIGINT;
    v_admin_role BIGINT;
BEGIN
    SELECT id INTO v_admin_role FROM roles
    WHERE tenant_id = v_tid AND code = 'SYS_ADMIN' AND deleted_at IS NULL
    ORDER BY id LIMIT 1;

    IF EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'SALES_ORDER' AND status = 'ENABLED') THEN
        RETURN;
    END IF;

    INSERT INTO approval_templates
        (tenant_id, business_type, code, name, version_no, template_type, status, priority,
         reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description,
         published_at, created_at, updated_at)
    VALUES
        (v_tid, 'SALES_ORDER', 'SO-DEFAULT', '销售订单默认审批模板', 1, 'MANUAL', 'ENABLED', 10,
         'RETURN_TO_SUBMITTER', 48, 24, 3, '销售订单提交后由系统管理员审批',
         now(), now(), now())
    RETURNING id INTO v_tpl;

    INSERT INTO approval_template_nodes
        (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign,
         timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
    VALUES
        (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now())
    RETURNING id INTO v_node;

    IF v_admin_role IS NOT NULL THEN
        INSERT INTO approval_node_assignees
            (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES
            (v_node, v_tid, 'ROLE', v_admin_role, '系统管理员', now());
    END IF;
END $$;