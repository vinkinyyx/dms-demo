-- V130: v4.3.0 R9 CUSTOMER 角色 RBAC 种子
-- 为每个厂家租户创建 CUSTOMER 角色 + “客户自助策略”，并仅授予客户自助所需资源：
--   销售订单(自己)、销退申请(自己)、合同(自己)、我的报表、我的授权(手术报台)、
--   手术报台、个人资料/地址、代金券查看(自己的可用券)。
-- 数据范围（强制 dealer_id = 自己）由后端 SalesScopeService / DataScope 在代码层保证，不靠前端。
-- 幂等：所有写入均 ON CONFLICT / NOT EXISTS 保护，可重复执行。

DO $$
DECLARE
    t RECORD;
    v_role_id BIGINT;
    v_strategy_id BIGINT;
    v_res_id BIGINT;
    v_res RECORD;
BEGIN
    FOR t IN SELECT id FROM tenants WHERE tenant_type = 'MANUFACTURER' AND deleted_at IS NULL
    LOOP
        -- 1. CUSTOMER 角色
        INSERT INTO roles (tenant_id, code, name, description, status, created_at, updated_at)
        VALUES (t.id, 'CUSTOMER', '客户', '注册客户自助账号：仅操作自己的订单/销退/合同/报表/授权/报台/资料', 'active', now(), now())
        ON CONFLICT (tenant_id, code) DO UPDATE SET name = EXCLUDED.name, updated_at = now()
        RETURNING id INTO v_role_id;

        IF v_role_id IS NULL THEN
            SELECT id INTO v_role_id FROM roles WHERE tenant_id = t.id AND code = 'CUSTOMER';
        END IF;

        -- 2. 客户自助策略
        INSERT INTO strategies (tenant_id, name, description, status, created_at, updated_at)
        VALUES (t.id, '客户自助策略', '注册客户自助下单与自助查询所需的最小权限集合', 'active', now(), now())
        ON CONFLICT DO NOTHING;
        SELECT id INTO v_strategy_id FROM strategies
        WHERE tenant_id = t.id AND name = '客户自助策略' AND deleted_at IS NULL
        ORDER BY id LIMIT 1;

        -- 3. 角色-策略绑定
        INSERT INTO role_strategies (role_id, strategy_id, created_at)
        VALUES (v_role_id, v_strategy_id, now())
        ON CONFLICT DO NOTHING;

        -- 4. 确保客户所需资源存在（缺失则补菜单/按钮/API 资源），再绑定策略
        --    资源 code 采用既有菜单/业务权限命名。
        FOR v_res IN
            SELECT code, name, type, ops, path FROM (VALUES
                ('menu.order',      '销售订单', 'menu',   ARRAY['view','create']::varchar[],        '/order'),
                ('menu.rma',        '销退申请', 'menu',   ARRAY['view','create']::varchar[],        '/rma'),
                ('menu.contract',   '合同管理', 'menu',   ARRAY['view']::varchar[],                 '/contract'),
                ('menu.report',     '我的报表', 'menu',   ARRAY['view','export']::varchar[],        '/report'),
                ('menu.surgery',    '手术报台', 'menu',   ARRAY['view','create']::varchar[],        '/surgery'),
                ('menu.profile',    '个人资料', 'menu',   ARRAY['view','update']::varchar[],        '/profile'),
                ('order:view',      '销售订单查看', 'button', ARRAY['view']::varchar[],            NULL),
                ('order:create',    '销售订单创建', 'button', ARRAY['create']::varchar[],          NULL),
                ('rma:view',        '销退单查看',   'button', ARRAY['view']::varchar[],            NULL),
                ('rma:create',      '销退单申请',   'button', ARRAY['create']::varchar[],          NULL),
                ('contract:view',   '合同查看',     'button', ARRAY['view']::varchar[],            NULL),
                ('report:view',     '报表查看',     'button', ARRAY['view','export']::varchar[],   NULL),
                ('authorization:view','我的授权查看','button', ARRAY['view']::varchar[],           NULL),
                ('surgery:view',    '手术报台查看', 'button', ARRAY['view']::varchar[],            NULL),
                ('surgery:create',  '手术报台申请', 'button', ARRAY['create']::varchar[],          NULL),
                ('dealer:view',     '客户资料查看', 'button', ARRAY['view']::varchar[],            NULL),
                ('dealer_contact:view','联系人查看','button', ARRAY['view']::varchar[],            NULL),
                ('dealer_address:view','地址查看', 'button', ARRAY['view']::varchar[],            NULL),
                ('customer_voucher:view','我的代金券查看','button', ARRAY['view']::varchar[],      NULL),
                ('api.order',       '订单API',    'api',    ARRAY['call']::varchar[],              '/api/orders/**'),
                ('api.sales_order', '销售订单API','api',    ARRAY['call']::varchar[],              '/api/sales-orders/**'),
                ('api.rma',         '销退API',    'api',    ARRAY['call']::varchar[],              '/api/rma-orders/**'),
                ('api.contract',    '合同API',    'api',    ARRAY['call']::varchar[],              '/api/contracts/**'),
                ('api.report',      '报表API',    'api',    ARRAY['call']::varchar[],              '/api/reports/**'),
                ('api.authorization','授权API',   'api',    ARRAY['call']::varchar[],              '/api/authorizations/**'),
                ('api.surgery',     '手术报台API','api',   ARRAY['call']::varchar[],              '/api/surgery-reports/**'),
                ('api.dealer',      '客户资料API','api',   ARRAY['call']::varchar[],              '/api/dealers/**'),
                ('api.customer_voucher','我的代金券API','api', ARRAY['call']::varchar[],          '/api/customer-vouchers/**'),
                ('api.auth',        '认证API',    'api',    ARRAY['call']::varchar[],              '/api/auth/**')
            ) AS x(code, name, type, ops, path)
        LOOP
            INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
            VALUES (t.id, v_res.code, v_res.name, v_res.type, v_res.ops, v_res.path, 'active', now(), now())
            ON CONFLICT (tenant_id, code) DO NOTHING;

            SELECT id INTO v_res_id FROM resources
            WHERE tenant_id = t.id AND code = v_res.code AND deleted_at IS NULL;

            INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
            VALUES (v_strategy_id, v_res_id, v_res.ops, now())
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;
