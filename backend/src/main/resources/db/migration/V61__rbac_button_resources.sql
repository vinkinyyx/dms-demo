-- V61: 给所有租户补 button 类型的 rbac_resources（D13 配套）
-- 说明：现有 resources 表只有 menu/api 类型；v3.8.7 引入的 platform_button_configs.permissionCode
--       需要在 rbac_resources 中存在 type='button' 的对应记录，才能被角色策略引用。
-- 策略：对每个非系统租户，幂等地插入本租户缺失的 button 资源（基于 code 唯一键）。

-- ============ 1. 业务表 button 资源清单（与 platform_button_configs.permissionCode 一一对应） ============
-- 这些 code 是 V59 预置的 platform_button_configs.permissionCode + 未来页面要用的扩展。
-- 新增 button 资源时，往这里 + platform_button_configs 同时加。

-- ============ 2. 批量插入（仅对非系统租户；系统模板租户 00000000 不补，业务租户按 tenant_type 区分） ============
DO $$
DECLARE
    t_id UUID;
    bt TEXT[] := ARRAY[
        -- 通用（所有页面通用按钮：search / reset / import / export / create）
        'page:search',  'page:reset',  'page:import',  'page:export',  'page:create',
        -- 经销商管理
        'dealer:search', 'dealer:import', 'dealer:export', 'dealer:create', 'dealer:view', 'dealer:edit', 'dealer:delete',
        -- 经销商申请表
        'dealer_application:search', 'dealer_application:create', 'dealer_application:view',
        'dealer_application:edit',   'dealer_application:submit', 'dealer_application:approve', 'dealer_application:delete',
        -- 销售订单
        'sales_order:search', 'sales_order:create', 'sales_order:view', 'sales_order:edit',
        'sales_order:submit', 'sales_order:approve', 'sales_order:export', 'sales_order:delete',
        -- 采购订单
        'purchase_order:search', 'purchase_order:create', 'purchase_order:view', 'purchase_order:edit',
        'purchase_order:submit', 'purchase_order:approve', 'purchase_order:import', 'purchase_order:export', 'purchase_order:delete',
        -- 收货入库
        'receipt:search', 'receipt:create', 'receipt:view', 'receipt:edit', 'receipt:confirm', 'receipt:cancel', 'receipt:export', 'receipt:delete',
        -- 销售出库
        'sales_out:search', 'sales_out:create', 'sales_out:view', 'sales_out:edit', 'sales_out:confirm', 'sales_out:cancel', 'sales_out:export', 'sales_out:delete',
        -- 库存查询
        'inventory:search', 'inventory:view', 'inventory:export', 'inventory:adjust',
        -- 库存移动
        'stock_move:search', 'stock_move:create', 'stock_move:view', 'stock_move:confirm', 'stock_move:cancel', 'stock_move:delete',
        -- 产品
        'product:search', 'product:create', 'product:view', 'product:edit', 'product:import', 'product:export', 'product:delete',
        -- 产品对码
        'product_mapping:search', 'product_mapping:import', 'product_mapping:export', 'product_mapping:view',
        -- 客户/医院/终端
        'hospital:search', 'hospital:create', 'hospital:view', 'hospital:edit', 'hospital:import', 'hospital:export', 'hospital:delete',
        -- 合同
        'contract:search', 'contract:create', 'contract:view', 'contract:edit', 'contract:submit', 'contract:approve', 'contract:export', 'contract:delete',
        -- 报表
        'report:view', 'report:export',
        -- 操作日志 / API 日志
        'api_log:search', 'api_log:export', 'api_log:view',
        -- 岗位
        'position:search', 'position:create', 'position:view', 'position:edit', 'position:delete', 'position:bind_user', 'position:bind_dealer',
        -- 用户
        'user:search', 'user:create', 'user:view', 'user:edit', 'user:reset_password', 'user:unlock', 'user:delete',
        -- 角色
        'role:search', 'role:create', 'role:view', 'role:edit', 'role:assign', 'role:delete',
        -- 字典
        'dict:search', 'dict:create', 'dict:edit', 'dict:delete',
        -- 菜单
        'menu:search', 'menu:create', 'menu:edit', 'menu:enable', 'menu:disable',
        -- 租户
        'tenant:search', 'tenant:create', 'tenant:view', 'tenant:edit', 'tenant:enable', 'tenant:disable'
    ];
    code_one TEXT;
    inserted INT := 0;
BEGIN
    FOR t_id IN SELECT id FROM tenants WHERE id <> '00000000-0000-0000-0000-000000000000'::uuid AND deleted_at IS NULL LOOP
        FOREACH code_one IN ARRAY bt LOOP
            -- 跳过已存在
            IF NOT EXISTS (
                SELECT 1 FROM resources
                WHERE tenant_id = t_id AND code = code_one AND deleted_at IS NULL
            ) THEN
                INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
                VALUES (t_id, code_one, code_one, 'button', ARRAY['read','write']::varchar[], NULL, 'active', now(), now());
                inserted := inserted + 1;
            END IF;
        END LOOP;
    END LOOP;
    RAISE NOTICE 'Inserted % button resources across tenants', inserted;
END $$;