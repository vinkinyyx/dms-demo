-- V145: 补全新开户经销商租户缺失的 button 权限资源
-- 背景：TenantRoleProvisioner 开户时仅创建 type='menu' 资源，不创建 type='button' 资源，
--       导致 V61 之后开户的经销商租户（如 DEALER_D1、苏州康宁）无业务按钮权限码，
--       前端菜单（purchase_order:view / receipt:view / sales_order:view 等）全部不可见、
--       业务路由 403。本迁移以模板经销商租户 DEALER_A1 (22222222-0000-...-001) 的
--       215 个 button 资源为基准，幂等复制到所有缺 button 的 DEALER 租户，
--       并绑定到各租户 DEALER_ADMIN 角色关联的管理员策略（与模板策略 17 行为一致，{view}）。
-- 幂等：资源存在性按 (tenant_id, code) NOT EXISTS 判断；绑定按 (strategy_id, resource_id) NOT EXISTS 判断。

DO $$
DECLARE
    v_tpl_tenant UUID := '22222222-0000-0000-0000-000000000001';
    v_ten RECORD;
    v_adm_strategy BIGINT;
    v_btn RECORD;
    v_new_id BIGINT;
BEGIN
    FOR v_ten IN
        SELECT t.id
        FROM tenants t
        WHERE t.tenant_type = 'DEALER'
          AND NOT EXISTS (
              SELECT 1 FROM resources r
              WHERE r.tenant_id = t.id AND r.type = 'button'
          )
    LOOP
        SELECT s.id INTO v_adm_strategy
        FROM strategies s
        JOIN role_strategies rs ON rs.strategy_id = s.id
        JOIN roles r ON r.id = rs.role_id
        WHERE r.tenant_id = v_ten.id AND r.code = 'DEALER_ADMIN'
        ORDER BY s.id
        LIMIT 1;

        IF v_adm_strategy IS NULL THEN
            RAISE NOTICE 'V145: tenant % 无 DEALER_ADMIN 策略，跳过', v_ten.id;
            CONTINUE;
        END IF;

        FOR v_btn IN
            SELECT code, name, type, operations, path, status
            FROM resources
            WHERE tenant_id = v_tpl_tenant AND type = 'button'
        LOOP
            SELECT id INTO v_new_id
            FROM resources
            WHERE tenant_id = v_ten.id AND code = v_btn.code;

            IF v_new_id IS NULL THEN
                INSERT INTO resources (tenant_id, code, name, type, parent_id, operations, path, status)
                VALUES (v_ten.id, v_btn.code, v_btn.name, 'button', NULL,
                        COALESCE(v_btn.operations, ARRAY['view']::varchar[]),
                        v_btn.path, COALESCE(v_btn.status, 'active'))
                RETURNING id INTO v_new_id;
            END IF;

            IF NOT EXISTS (
                SELECT 1 FROM strategy_resources
                WHERE strategy_id = v_adm_strategy AND resource_id = v_new_id
            ) THEN
                INSERT INTO strategy_resources (strategy_id, resource_id, operations)
                VALUES (v_adm_strategy, v_new_id, ARRAY['view']::varchar[]);
            END IF;
        END LOOP;

        RAISE NOTICE 'V145: tenant % button 权限补全完成，admin 策略 %', v_ten.id, v_adm_strategy;
    END LOOP;
END $$;
