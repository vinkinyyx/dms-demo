-- =====================================================================
-- V7 Seed Demo Data - 生成 default 租户全量演示数据
-- ---------------------------------------------------------------------
-- 依赖：V1~V6 表结构与字典/系统模板已就位。
-- 幂等：所有插入前先按 tenant_id 清空 default 租户数据；
--       系统模板租户（SYSTEM，UUID=000...）数据不受影响。
-- 时间：分区表 inventory_transactions / audit_logs 限定 2026-07~2026-12。
-- 兼容：PostgreSQL 14 + Flyway 8.x
-- =====================================================================

DO $$ BEGIN RAISE NOTICE 'V7 Seed 开始清理 default 租户旧数据...'; END $$;

-- ---------------------------------------------------------------------
-- 0. 幂等清理（按外键反向顺序）
-- ---------------------------------------------------------------------
DELETE FROM audit_logs             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM notifications          WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM approval_history       WHERE task_id IN (SELECT id FROM approval_tasks WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM approval_tasks         WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM async_jobs             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM dealer_kpi_snapshots   WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM rebate_settlements     WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM rebate_previews        WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM promotion_status_logs  WHERE promotion_id IN (SELECT id FROM promotions WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM promotion_rules        WHERE promotion_id IN (SELECT id FROM promotions WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM promotions             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM sales_invoices         WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM purchase_invoices      WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM rma_orders             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM rma_authorizations     WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM distribution_lines     WHERE shipment_id IN (SELECT id FROM distribution_shipments WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM distribution_shipments WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM sales_out_facts        WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM sales_out_lines        WHERE sales_out_id IN (SELECT id FROM sales_outs WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM sales_outs             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM stocktake_lines        WHERE stocktake_id IN (SELECT id FROM stocktakes WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM stocktakes             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM adjustment_lines       WHERE adjustment_id IN (SELECT id FROM inventory_adjustments WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM inventory_adjustments  WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM loan_lines             WHERE loan_id IN (SELECT id FROM loans WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM loans                  WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM stock_move_lines       WHERE move_id IN (SELECT id FROM stock_moves WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM stock_moves            WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM inventory_transactions WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM inventory              WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM receipt_lines          WHERE receipt_id IN (SELECT id FROM receipts WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM receipts               WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM order_promotion_hits   WHERE order_id IN (SELECT id FROM orders WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM order_status_history   WHERE order_id IN (SELECT id FROM orders WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM order_lines            WHERE order_id IN (SELECT id FROM orders WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM orders                 WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM authorizations         WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM temp_authorizations    WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM contract_signatures    WHERE contract_id IN (SELECT id FROM contracts WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM contract_attachments   WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM contracts              WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM contract_diff          WHERE application_id IN (SELECT id FROM contract_applications WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM contract_applications  WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM price_lists            WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM warehouses             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM dealer_addresses       WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM dealers                WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM hospitals              WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM products               WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM product_categories     WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM regions                WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM user_login_logs        WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM data_scopes            WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM user_roles             WHERE user_id IN (SELECT id FROM users WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM users                  WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM role_strategies        WHERE role_id IN (SELECT id FROM roles WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM strategy_resources     WHERE strategy_id IN (SELECT id FROM strategies WHERE tenant_id = '11111111-1111-1111-1111-111111111111');
DELETE FROM roles                  WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM strategies             WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM resources              WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM org_units              WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM tenant_modules         WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM tenants                WHERE id        = '11111111-1111-1111-1111-111111111111';

DO $$ BEGIN RAISE NOTICE 'V7 Seed 清理完成，开始插入基础数据...'; END $$;

-- =====================================================================
-- 1. 租户（1 条）
-- =====================================================================
INSERT INTO tenants (id, code, name, industry, timezone, status, modules_enabled, quota, attrs, contact_name, contact_email, contact_phone, effective_from, effective_to)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'default',
    'Default 演示租户',
    'medical',
    'Asia/Shanghai',
    'active',
    '{"contract":true,"order":true,"inventory":true,"sales":true,"promotion":true,"report":true}'::jsonb,
    '{"max_users":500,"max_dealers":1000}'::jsonb,
    '{"primary_color":"#2C4B8E"}'::jsonb,
    '张管理', 'admin@dms.local', '13800000000',
    '2026-01-01', '2030-12-31'
);

INSERT INTO tenant_modules (tenant_id, module_code, enabled) VALUES
    ('11111111-1111-1111-1111-111111111111','contract',true),
    ('11111111-1111-1111-1111-111111111111','order',true),
    ('11111111-1111-1111-1111-111111111111','inventory',true),
    ('11111111-1111-1111-1111-111111111111','sales',true),
    ('11111111-1111-1111-1111-111111111111','promotion',true),
    ('11111111-1111-1111-1111-111111111111','report',true);

-- =====================================================================
-- 2. 组织架构 org_units（20：1 总部 + 4 大区 + 15 分公司）
-- =====================================================================
INSERT INTO org_units (tenant_id, parent_id, code, name, level, path, type)
VALUES ('11111111-1111-1111-1111-111111111111', NULL, 'HQ', '总部', 1, '/HQ', 'headquarters');

-- 4 大区
INSERT INTO org_units (tenant_id, parent_id, code, name, level, path, type)
SELECT '11111111-1111-1111-1111-111111111111',
       (SELECT id FROM org_units WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='HQ'),
       'RGN' || LPAD(g::text,2,'0'),
       (ARRAY['华东大区','华北大区','华南大区','华西大区'])[g],
       2,
       '/HQ/RGN' || LPAD(g::text,2,'0'),
       'region'
FROM generate_series(1,4) g;

-- 15 分公司
INSERT INTO org_units (tenant_id, parent_id, code, name, level, path, type)
SELECT '11111111-1111-1111-1111-111111111111',
       (SELECT id FROM org_units WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='RGN'||LPAD(((g-1)%4+1)::text,2,'0')),
       'BR' || LPAD(g::text,2,'0'),
       '分公司-' || LPAD(g::text,2,'0'),
       3,
       '/HQ/RGN'||LPAD(((g-1)%4+1)::text,2,'0')||'/BR'||LPAD(g::text,2,'0'),
       'branch'
FROM generate_series(1,15) g;

-- =====================================================================
-- 3. 用户 users（20：10 厂商 + 10 经销商，含 admin + 2 微信绑定）
--    密码统一 Sh123456 → bcrypt hash
-- =====================================================================
-- admin 超管
INSERT INTO users (tenant_id, username, name, user_type, password_hash, must_change_password, email, phone, status)
VALUES ('11111111-1111-1111-1111-111111111111', 'admin', '超级管理员', 'vendor',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', false,
        'admin@dms.local', '13800000001', 'active');

-- 9 厂商用户 vendor02~vendor10
INSERT INTO users (tenant_id, username, name, user_type, password_hash, must_change_password, email, phone, org_id, status, wechat_openid, wechat_bound_at)
SELECT '11111111-1111-1111-1111-111111111111',
       'vendor' || LPAD(g::text,2,'0'),
       '厂商用户' || g,
       'vendor',
       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       false,
       'vendor' || g || '@dms.local',
       '138' || LPAD((10000000+g)::text,8,'0'),
       (SELECT id FROM org_units WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='BR'||LPAD(((g-1)%15+1)::text,2,'0')),
       'active',
       CASE WHEN g=1 THEN 'MOCK_OPENID_001' ELSE NULL END,
       CASE WHEN g=1 THEN NOW() ELSE NULL END
FROM generate_series(2,10) g;

-- 10 经销商用户 dealer01~dealer10
INSERT INTO users (tenant_id, username, name, user_type, password_hash, must_change_password, email, phone, status, wechat_openid, wechat_bound_at)
SELECT '11111111-1111-1111-1111-111111111111',
       'dealer' || LPAD(g::text,2,'0'),
       '经销商用户' || g,
       'dealer',
       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       false,
       'dealer' || g || '@dms.local',
       '139' || LPAD((10000000+g)::text,8,'0'),
       'active',
       CASE WHEN g=1 THEN 'MOCK_OPENID_002' ELSE NULL END,
       CASE WHEN g=1 THEN NOW() ELSE NULL END
FROM generate_series(1,10) g;

-- =====================================================================
-- 4. RBAC 权限体系
--    8 roles + 10 strategies + 20 resources + 关联
-- =====================================================================
-- resources（20 条：菜单 + API）
INSERT INTO resources (tenant_id, code, name, type, operations, path)
SELECT '11111111-1111-1111-1111-111111111111',
       v.code, v.name, v.type, v.ops, v.path
FROM (VALUES
    ('menu.dashboard',   '工作台',        'menu',   ARRAY['view'],                       '/dashboard'),
    ('menu.tenant',      '租户管理',      'menu',   ARRAY['view','create','update'],     '/tenant'),
    ('menu.user',        '用户管理',      'menu',   ARRAY['view','create','update','delete'], '/user'),
    ('menu.role',        '角色管理',      'menu',   ARRAY['view','create','update','delete'], '/role'),
    ('menu.dealer',      '经销商管理',    'menu',   ARRAY['view','create','update','delete'], '/dealer'),
    ('menu.product',     '产品管理',      'menu',   ARRAY['view','create','update','delete'], '/product'),
    ('menu.contract',    '合同管理',      'menu',   ARRAY['view','create','update','approve'],'/contract'),
    ('menu.order',       '订单管理',      'menu',   ARRAY['view','create','update','approve'],'/order'),
    ('menu.receipt',     '收货管理',      'menu',   ARRAY['view','create'],              '/receipt'),
    ('menu.inventory',   '库存管理',      'menu',   ARRAY['view','update'],              '/inventory'),
    ('menu.sales_out',   '销售出库',      'menu',   ARRAY['view','create'],              '/sales-out'),
    ('menu.rma',         '退换货',        'menu',   ARRAY['view','create','approve'],    '/rma'),
    ('menu.promotion',   '促销管理',      'menu',   ARRAY['view','create','approve'],    '/promotion'),
    ('menu.report',      '报表画像',      'menu',   ARRAY['view','export'],              '/report'),
    ('api.auth',         '认证接口',      'api',    ARRAY['call'],                       '/api/auth/**'),
    ('api.tenant',       '租户 API',      'api',    ARRAY['call'],                       '/api/tenants/**'),
    ('api.user',         '用户 API',      'api',    ARRAY['call'],                       '/api/users/**'),
    ('api.order',        '订单 API',      'api',    ARRAY['call'],                       '/api/orders/**'),
    ('api.contract',     '合同 API',      'api',    ARRAY['call'],                       '/api/contracts/**'),
    ('api.inventory',    '库存 API',      'api',    ARRAY['call'],                       '/api/inventory/**')
) AS v(code,name,type,ops,path);

-- strategies（10 条）
INSERT INTO strategies (tenant_id, name, description)
SELECT '11111111-1111-1111-1111-111111111111', v.name, v.description
FROM (VALUES
    ('全部权限','系统管理员默认策略'),
    ('销售管理策略','销售/经理数据管理'),
    ('客服策略','客服日常查询'),
    ('商务策略','商务合同与订单'),
    ('财务策略','财务发票与结算'),
    ('合同专员策略','合同起草与归档'),
    ('经销商基础策略','经销商查看自身数据'),
    ('促销管理策略','促销活动创建审批'),
    ('库存管理策略','库存与收货管理'),
    ('报表查看策略','报表画像查看导出')
) AS v(name,description);

-- roles（8 条）
INSERT INTO roles (tenant_id, code, name, description)
SELECT '11111111-1111-1111-1111-111111111111', v.code, v.name, v.description
FROM (VALUES
    ('SYS_ADMIN',      '系统管理员',   '拥有全部功能权限'),
    ('SALES_MGR',      '销售经理',     '大区/分公司销售管理'),
    ('SALES',          '销售',         '一线销售'),
    ('CS',             '客服',         '客服支持'),
    ('BIZ',            '商务',         '合同/订单商务处理'),
    ('FIN',            '财务',         '发票 / 结算 / 返利'),
    ('CONTRACT_SPEC',  '合同专员',     '合同起草与归档'),
    ('DEALER_ADMIN',   '经销商管理员', '经销商侧管理员')
) AS v(code,name,description);

-- role_strategies：SYS_ADMIN 绑全部策略；其他简化各绑 2 策略
INSERT INTO role_strategies (role_id, strategy_id)
SELECT r.id, s.id
FROM roles r
CROSS JOIN strategies s
WHERE r.tenant_id='11111111-1111-1111-1111-111111111111'
  AND s.tenant_id='11111111-1111-1111-1111-111111111111'
  AND r.code='SYS_ADMIN';

INSERT INTO role_strategies (role_id, strategy_id)
SELECT r.id, s.id
FROM roles r
JOIN strategies s ON s.tenant_id=r.tenant_id
WHERE r.tenant_id='11111111-1111-1111-1111-111111111111'
  AND r.code<>'SYS_ADMIN'
  AND s.name IN ('销售管理策略','报表查看策略');

-- strategy_resources：所有策略暂时绑全部 resource（简化演示）
INSERT INTO strategy_resources (strategy_id, resource_id, operations)
SELECT s.id, r.id, r.operations
FROM strategies s
CROSS JOIN resources r
WHERE s.tenant_id='11111111-1111-1111-1111-111111111111'
  AND r.tenant_id='11111111-1111-1111-1111-111111111111';

-- user_roles：admin → SYS_ADMIN；vendor* → SALES_MGR/SALES 轮换；dealer* → DEALER_ADMIN
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u JOIN roles r ON r.tenant_id=u.tenant_id
WHERE u.tenant_id='11111111-1111-1111-1111-111111111111'
  AND ((u.username='admin' AND r.code='SYS_ADMIN')
    OR (u.username LIKE 'vendor%' AND r.code='SALES')
    OR (u.username LIKE 'dealer%' AND r.code='DEALER_ADMIN'));

-- =====================================================================
-- 5. 主数据 - regions（30：5 大区 + 15 省 + 10 市）
-- =====================================================================
INSERT INTO regions (tenant_id, code, name, parent_id, level)
SELECT '11111111-1111-1111-1111-111111111111',
       'RG-L1-' || LPAD(g::text,2,'0'),
       (ARRAY['华东','华北','华南','华西','华中'])[g],
       NULL, 1
FROM generate_series(1,5) g;

INSERT INTO regions (tenant_id, code, name, parent_id, level)
SELECT '11111111-1111-1111-1111-111111111111',
       'RG-L2-' || LPAD(g::text,2,'0'),
       (ARRAY['上海市','江苏省','浙江省','安徽省','福建省',
              '北京市','天津市','河北省','山东省','山西省',
              '广东省','广西省','海南省','四川省','重庆市'])[g],
       (SELECT id FROM regions WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='RG-L1-'||LPAD((((g-1)/3)%5+1)::text,2,'0')),
       2
FROM generate_series(1,15) g;

INSERT INTO regions (tenant_id, code, name, parent_id, level)
SELECT '11111111-1111-1111-1111-111111111111',
       'RG-L3-' || LPAD(g::text,2,'0'),
       (ARRAY['苏州市','杭州市','宁波市','南京市','合肥市',
              '济南市','青岛市','广州市','深圳市','成都市'])[g],
       (SELECT id FROM regions WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='RG-L2-'||LPAD((((g-1)%15)+1)::text,2,'0')),
       3
FROM generate_series(1,10) g;

-- =====================================================================
-- 6. 产品分类（8：3 大类 + 5 子类）
-- =====================================================================
INSERT INTO product_categories (tenant_id, code, name, parent_id, level)
SELECT '11111111-1111-1111-1111-111111111111', v.code, v.name, NULL, 1
FROM (VALUES ('CAT-DEV','器械'),('CAT-CON','耗材'),('CAT-REA','试剂')) AS v(code,name);

INSERT INTO product_categories (tenant_id, code, name, parent_id, level)
SELECT '11111111-1111-1111-1111-111111111111', v.code, v.name,
       (SELECT id FROM product_categories WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code=v.parent),
       2
FROM (VALUES
    ('CAT-DEV-01','影像器械','CAT-DEV'),
    ('CAT-DEV-02','手术器械','CAT-DEV'),
    ('CAT-CON-01','一次性耗材','CAT-CON'),
    ('CAT-CON-02','植入耗材','CAT-CON'),
    ('CAT-REA-01','诊断试剂','CAT-REA')
) AS v(code,name,parent);

-- =====================================================================
-- 7. 产品（200：PROD-000001~200，随机子分类，价格 100~10000）
-- =====================================================================
INSERT INTO products (tenant_id, code, name_cn, name_en, category_id, spec, unit, current_price, tax_rate, udi_required, warn_months, safety_qty, min_order_qty, status)
SELECT '11111111-1111-1111-1111-111111111111',
       'PROD-' || LPAD(g::text,6,'0'),
       '演示产品-' || g,
       'Demo Product ' || g,
       (SELECT id FROM product_categories WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND level=2 ORDER BY id LIMIT 1 OFFSET ((g-1) % 5)),
       'SPEC-' || (10 + (g % 30)),
       (ARRAY['盒','支','件','套','瓶'])[1 + (g % 5)],
       ROUND((100 + random() * 9900)::numeric, 2),
       0.13,
       true,
       3,
       10,
       1,
       'active'
FROM generate_series(1,200) g;

-- =====================================================================
-- 8. 医院（100：HP-001~100，随机 region）
-- =====================================================================
INSERT INTO hospitals (tenant_id, code, name, type, level, region_id, address, contact, phone, status)
SELECT '11111111-1111-1111-1111-111111111111',
       'HP-' || LPAD(g::text,3,'0'),
       '演示医院-' || g,
       (ARRAY['公立','民营','专科'])[1 + (g % 3)],
       (ARRAY['三甲','三乙','二甲'])[1 + (g % 3)],
       (SELECT id FROM regions WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND level=2 ORDER BY id LIMIT 1 OFFSET (g % 15)),
       '演示地址-' || g,
       '联系人-' || g,
       '021' || LPAD((10000000+g)::text,8,'0'),
       'active'
FROM generate_series(1,100) g;

-- =====================================================================
-- 9. 经销商（50：D00001~50，前 20 一级，后 30 二级挂一级）
-- =====================================================================
-- 先插入 20 个一级
INSERT INTO dealers (tenant_id, code, name, level, parent_dealer_id, legal_person, usc_no, reg_address, reg_capital, founded_at, business_scope, gsp_status, gsp_expire, region_id, contact_name, contact_phone, contact_email, status)
SELECT '11111111-1111-1111-1111-111111111111',
       'D' || LPAD(g::text,5,'0'),
       '一级经销商-' || g,
       'T1', NULL,
       '法人-' || g,
       '91310000' || LPAD((100000000+g)::text,10,'0'),
       '注册地址-' || g,
       ROUND((5000000 + random()*95000000)::numeric,2),
       DATE '2020-01-01' + ((g*17) % 1000) * INTERVAL '1 day',
       '医疗器械销售',
       'active',
       DATE '2028-12-31',
       (SELECT id FROM regions WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND level=2 ORDER BY id LIMIT 1 OFFSET (g % 15)),
       '联系人-' || g,
       '135' || LPAD((10000000+g)::text,8,'0'),
       'dealer'||g||'@example.com',
       'active'
FROM generate_series(1,20) g;

-- 再插入 30 个二级，parent 指向前 20 一级
INSERT INTO dealers (tenant_id, code, name, level, parent_dealer_id, legal_person, usc_no, reg_address, reg_capital, founded_at, business_scope, gsp_status, gsp_expire, region_id, contact_name, contact_phone, contact_email, status)
SELECT '11111111-1111-1111-1111-111111111111',
       'D' || LPAD((20+g)::text,5,'0'),
       '二级经销商-' || g,
       'T2',
       (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='D'||LPAD(((g-1)%20+1)::text,5,'0')),
       '法人-T2-' || g,
       '91310000' || LPAD((200000000+g)::text,10,'0'),
       '注册地址-T2-' || g,
       ROUND((1000000 + random()*4000000)::numeric,2),
       DATE '2021-01-01' + ((g*11) % 800) * INTERVAL '1 day',
       '医疗器械销售',
       'active',
       DATE '2028-12-31',
       (SELECT id FROM regions WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND level=2 ORDER BY id LIMIT 1 OFFSET (g % 15)),
       'T2 联系人-' || g,
       '136' || LPAD((10000000+g)::text,8,'0'),
       'dealerT2'||g||'@example.com',
       'active'
FROM generate_series(1,30) g;

-- 默认收货地址（每 dealer 一条 default）
INSERT INTO dealer_addresses (tenant_id, dealer_id, is_default, contact_name, phone, province, city, district, address, postal_code)
SELECT '11111111-1111-1111-1111-111111111111',
       d.id, true, d.contact_name, d.contact_phone, '上海市', '上海市', '浦东新区', '演示地址' || d.code, '200000'
FROM dealers d
WHERE d.tenant_id='11111111-1111-1111-1111-111111111111';

-- =====================================================================
-- 10. 仓库（150：每 dealer 3 个：main / sub / hospital）
-- =====================================================================
-- main
INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, hospital_id, address, status)
SELECT '11111111-1111-1111-1111-111111111111',
       d.id, 'WH-MAIN', d.name || '-主仓', 'main', NULL, '主仓地址-' || d.code, 'active'
FROM dealers d WHERE d.tenant_id='11111111-1111-1111-1111-111111111111';

-- sub
INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, hospital_id, address, status)
SELECT '11111111-1111-1111-1111-111111111111',
       d.id, 'WH-SUB', d.name || '-子仓', 'sub', NULL, '子仓地址-' || d.code, 'active'
FROM dealers d WHERE d.tenant_id='11111111-1111-1111-1111-111111111111';

-- hospital 仓：每 dealer 一个，随机挂一家医院
INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, hospital_id, address, status)
SELECT '11111111-1111-1111-1111-111111111111',
       d.id, 'WH-HP', d.name || '-医院仓', 'hospital',
       (SELECT id FROM hospitals WHERE tenant_id='11111111-1111-1111-1111-111111111111'
         ORDER BY id LIMIT 1 OFFSET ((d.id % 100)::int)),
       '医院仓地址-' || d.code, 'active'
FROM dealers d WHERE d.tenant_id='11111111-1111-1111-1111-111111111111';

-- =====================================================================
-- 11. 价格 price_lists（200 条：每产品一条生效价，对应 current_price）
-- =====================================================================
INSERT INTO price_lists (tenant_id, product_id, dealer_id, price, currency, valid_from, valid_to, source)
SELECT '11111111-1111-1111-1111-111111111111',
       p.id, NULL, p.current_price, 'CNY',
       DATE '2026-01-01', DATE '2030-12-31', 'ERP'
FROM products p WHERE p.tenant_id='11111111-1111-1111-1111-111111111111';

-- =====================================================================
-- 12. 合同申请 contract_applications（30 条，状态混合）
-- =====================================================================
INSERT INTO contract_applications (tenant_id, code, application_type, contract_category, dealer_id, valid_from, valid_to, status, submitted_at, effective_at, remark)
SELECT '11111111-1111-1111-1111-111111111111',
       'HT-APP-' || LPAD(g::text,6,'0'),
       (ARRAY['NEW','MODIFY','RENEW'])[1 + (g % 3)],
       (ARRAY['SALES','AGENCY','FRANCHISE'])[1 + (g % 3)],
       (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)),
       DATE '2026-01-01' + ((g*3) % 60) * INTERVAL '1 day',
       DATE '2027-12-31',
       CASE WHEN g<=20 THEN 'effective'
            WHEN g<=25 THEN 'approving'
            WHEN g<=27 THEN 'rejected'
            ELSE 'draft' END,
       NOW() - ((g % 60) || ' days')::INTERVAL,
       CASE WHEN g<=20 THEN NOW() - ((g % 30) || ' days')::INTERVAL ELSE NULL END,
       '演示合同申请 ' || g
FROM generate_series(1,30) g;

-- =====================================================================
-- 13. 合同 contracts（25 生效）
-- =====================================================================
INSERT INTO contracts (tenant_id, code, application_id, dealer_id, category, valid_from, valid_to, status, pdf_url, ca_serial_no, dealer_signed_at, vendor_signed_at)
SELECT '11111111-1111-1111-1111-111111111111',
       'HT-' || LPAD(g::text,6,'0'),
       (SELECT id FROM contract_applications WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='HT-APP-'||LPAD(g::text,6,'0')),
       (SELECT dealer_id FROM contract_applications WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code='HT-APP-'||LPAD(g::text,6,'0')),
       'SALES',
       DATE '2026-01-01', DATE '2027-12-31',
       'effective',
       'http://minio:9000/dms/contracts/HT-' || LPAD(g::text,6,'0') || '.pdf',
       'CA-MOCK-' || LPAD(g::text,10,'0'),
       NOW() - ((g % 30) || ' days')::INTERVAL,
       NOW() - ((g % 25) || ' days')::INTERVAL
FROM generate_series(1,25) g;

-- =====================================================================
-- 14. 授权 authorizations（500 条：合同派生，多类型混合）
-- =====================================================================
INSERT INTO authorizations (tenant_id, dealer_id, contract_id, auth_type, product_id, region_id, valid_from, valid_to, status, source)
SELECT '11111111-1111-1111-1111-111111111111',
       c.dealer_id, c.id,
       (ARRAY['ORDER','SALES_TO_HOSPITAL','SALES_TO_SUB','RMA','MOVE','LOAN'])[1 + (g % 6)],
       (SELECT id FROM products WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 200)),
       (SELECT id FROM regions   WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND level=2 ORDER BY id LIMIT 1 OFFSET (g % 15)),
       DATE '2026-01-01', DATE '2027-12-31', 'active', 'contract'
FROM generate_series(1,500) g
CROSS JOIN LATERAL (SELECT id, dealer_id FROM contracts WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 25)) c;

-- =====================================================================
-- 15. 订单 orders（500：DRAFT100/SUBMITTED100/APPROVED200/COMPLETED50/REJECTED30/CANCELLED20）
--     金额随机 1w~10w，日期近 6 个月
-- =====================================================================
INSERT INTO orders (tenant_id, code, order_type, dealer_id, status, amount_incl_tax, discount_amount, final_amount, remark, expected_date, submitted_at, approved_at, closed_at, created_by, created_at)
SELECT '11111111-1111-1111-1111-111111111111',
       'PO-' || LPAD(g::text,8,'0'),
       (ARRAY['PURCHASE','SHORTAGE','CUSTOM'])[1 + (g % 3)],
       (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)),
       CASE
         WHEN g<=100 THEN 'DRAFT'
         WHEN g<=200 THEN 'SUBMITTED'
         WHEN g<=400 THEN 'APPROVED'
         WHEN g<=450 THEN 'COMPLETED'
         WHEN g<=480 THEN 'REJECTED'
         ELSE 'CANCELLED'
       END,
       ROUND((10000 + random()*90000)::numeric,2),
       ROUND((random()*5000)::numeric,2),
       ROUND((10000 + random()*90000)::numeric,2),
       '演示订单 ' || g,
       CURRENT_DATE + ((g % 30) || ' days')::INTERVAL,
       NOW() - ((g % 180) || ' days')::INTERVAL,
       CASE WHEN g>200 AND g<=450 THEN NOW() - ((g % 150) || ' days')::INTERVAL ELSE NULL END,
       CASE WHEN g>400 AND g<=450 THEN NOW() - ((g % 120) || ' days')::INTERVAL ELSE NULL END,
       1,
       NOW() - ((g % 180) || ' days')::INTERVAL
FROM generate_series(1,500) g;

-- =====================================================================
-- 16. 订单行 order_lines（2000：每单平均 4 行）
-- =====================================================================
INSERT INTO order_lines (order_id, product_id, qty, unit_price, tax_rate, sub_total, is_gift, seq)
SELECT o.id,
       (SELECT id FROM products WHERE tenant_id='11111111-1111-1111-1111-111111111111'
         ORDER BY id LIMIT 1 OFFSET ((o.id * 7 + s) % 200)),
       (1 + (s % 20))::numeric,
       ROUND((100 + random()*2000)::numeric,2),
       0.13,
       ROUND(((1 + (s % 20)) * (100 + random()*2000))::numeric,2),
       false,
       s
FROM orders o
CROSS JOIN generate_series(1,4) s
WHERE o.tenant_id='11111111-1111-1111-1111-111111111111';

-- =====================================================================
-- 17. 收货 receipts（400：COMPLETED 350 + PENDING 50）
-- =====================================================================
INSERT INTO receipts (tenant_id, code, receipt_type, ref_doc_type, dealer_id, warehouse_id, status, received_at, received_by, remark)
SELECT '11111111-1111-1111-1111-111111111111',
       'RC-' || LPAD(g::text,8,'0'),
       'PURCHASE', 'ORDER',
       d.id,
       (SELECT id FROM warehouses WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND dealer_id=d.id AND type='main' LIMIT 1),
       CASE WHEN g<=350 THEN 'COMPLETED' ELSE 'PENDING' END,
       NOW() - ((g % 150) || ' days')::INTERVAL,
       1,
       '演示收货 ' || g
FROM generate_series(1,400) g
CROSS JOIN LATERAL (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)) d;

-- =====================================================================
-- 18. 库存明细 inventory（3000 条）
-- =====================================================================
INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, batch_no, serial_no, prod_date, exp_date, qty, in_source)
SELECT '11111111-1111-1111-1111-111111111111',
       w.dealer_id,
       w.id,
       (SELECT id FROM products WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 200)),
       'BATCH-' || LPAD(g::text,6,'0'),
       'SN-' || LPAD(g::text,8,'0'),
       CURRENT_DATE - ((g % 90) || ' days')::INTERVAL,
       CURRENT_DATE + ((365 + (g % 365)) || ' days')::INTERVAL,
       (10 + (g % 100))::numeric,
       'RECEIPT'
FROM generate_series(1,3000) g
CROSS JOIN LATERAL (SELECT id, dealer_id FROM warehouses WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 150)) w
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 19. 库存流水 inventory_transactions（5000 条：分区表 2026-07~2026-12）
-- =====================================================================
INSERT INTO inventory_transactions (tenant_id, dealer_id, warehouse_id, product_id, batch_no, qty_change, txn_type, ref_doc_type, at_time, operator_id)
SELECT '11111111-1111-1111-1111-111111111111',
       w.dealer_id,
       w.id,
       (SELECT id FROM products WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 200)),
       'BATCH-' || LPAD((g % 3000 + 1)::text,6,'0'),
       CASE WHEN g % 2 = 0 THEN (1 + (g % 50))::numeric ELSE -(1 + (g % 20))::numeric END,
       (ARRAY['RECEIPT_IN','SALES_OUT','MOVE_IN','MOVE_OUT','ADJ_IN','ADJ_OUT'])[1 + (g % 6)],
       (ARRAY['RECEIPT','SALES_OUT','MOVE','ADJUSTMENT'])[1 + (g % 4)],
       TIMESTAMPTZ '2026-07-01 00:00:00+00' + ((g % 180) || ' days')::INTERVAL + ((g % 24) || ' hours')::INTERVAL,
       1
FROM generate_series(1,5000) g
CROSS JOIN LATERAL (SELECT id, dealer_id FROM warehouses WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 150)) w;

-- =====================================================================
-- 20. 移库 stock_moves（100 条）
-- =====================================================================
INSERT INTO stock_moves (tenant_id, code, dealer_id, src_warehouse_id, dst_warehouse_id, status, reason, operator_id, at_time)
SELECT '11111111-1111-1111-1111-111111111111',
       'MV-' || LPAD(g::text,6,'0'),
       d.id,
       (SELECT id FROM warehouses WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND dealer_id=d.id AND type='main' LIMIT 1),
       (SELECT id FROM warehouses WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND dealer_id=d.id AND type='sub'  LIMIT 1),
       'COMPLETED', '演示移库 ' || g, 1,
       NOW() - ((g % 120) || ' days')::INTERVAL
FROM generate_series(1,100) g
CROSS JOIN LATERAL (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)) d;

-- =====================================================================
-- 21. 销售出库 sales_outs（300：250 正常 + 50 红冲）
-- =====================================================================
INSERT INTO sales_outs (tenant_id, code, dealer_id, terminal_id, business_type, sales_date, is_red, status, amount_incl_tax, created_by, created_at)
SELECT '11111111-1111-1111-1111-111111111111',
       'SO-' || LPAD(g::text,8,'0'),
       (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)),
       (SELECT id FROM hospitals WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 100)),
       (ARRAY['HOSPITAL','SUB_DEALER'])[1 + (g % 2)],
       CURRENT_DATE - ((g % 180) || ' days')::INTERVAL,
       CASE WHEN g > 250 THEN true ELSE false END,
       'COMPLETED',
       ROUND((1000 + random()*50000)::numeric,2),
       1,
       NOW() - ((g % 180) || ' days')::INTERVAL
FROM generate_series(1,300) g;

-- =====================================================================
-- 22. 销售事实 sales_out_facts（800 条）
-- =====================================================================
INSERT INTO sales_out_facts (tenant_id, dealer_id, product_id, terminal_id, region_id, sales_date, qty, amount)
SELECT '11111111-1111-1111-1111-111111111111',
       (SELECT id FROM dealers  WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)),
       (SELECT id FROM products WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 200)),
       (SELECT id FROM hospitals WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 100)),
       (SELECT id FROM regions   WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND level=2 ORDER BY id LIMIT 1 OFFSET (g % 15)),
       CURRENT_DATE - ((g % 180) || ' days')::INTERVAL,
       (1 + (g % 30))::numeric,
       ROUND((500 + random()*20000)::numeric,2)
FROM generate_series(1,800) g;

-- =====================================================================
-- 23. RMA 授权 & RMA 订单（30 / 40）
-- =====================================================================
INSERT INTO rma_authorizations (tenant_id, code, dealer_id, quota_amount, quota_used, valid_from, valid_to, status, reason)
SELECT '11111111-1111-1111-1111-111111111111',
       'RMA-AUTH-' || LPAD(g::text,6,'0'),
       (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)),
       ROUND((50000 + random()*200000)::numeric,2),
       0,
       DATE '2026-01-01', DATE '2027-12-31', 'active',
       '演示退换货授权 ' || g
FROM generate_series(1,30) g;

INSERT INTO rma_orders (tenant_id, code, ref_rma_auth_id, dealer_id, rma_type, amount, status, reason, submitted_at, completed_at)
SELECT '11111111-1111-1111-1111-111111111111',
       'RMA-' || LPAD(g::text,8,'0'),
       (SELECT id FROM rma_authorizations WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 30)),
       (SELECT id FROM dealers WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 50)),
       (ARRAY['RETURN','EXCHANGE'])[1 + (g % 2)],
       ROUND((1000 + random()*30000)::numeric,2),
       CASE WHEN g<=25 THEN 'COMPLETED' WHEN g<=35 THEN 'APPROVING' ELSE 'DRAFT' END,
       '演示 RMA 单据 ' || g,
       NOW() - ((g % 90) || ' days')::INTERVAL,
       CASE WHEN g<=25 THEN NOW() - ((g % 60) || ' days')::INTERVAL ELSE NULL END
FROM generate_series(1,40) g;

-- =====================================================================
-- 24. 采购发票 purchase_invoices（300 条：对已完成订单）
-- =====================================================================
INSERT INTO purchase_invoices (tenant_id, ref_order_id, invoice_no, invoice_date, amount, tax_amount, tax_rate, uploaded_by, uploaded_at)
SELECT '11111111-1111-1111-1111-111111111111',
       o.id,
       'INV-' || LPAD(g::text,10,'0'),
       CURRENT_DATE - ((g % 120) || ' days')::INTERVAL,
       o.final_amount,
       ROUND((o.final_amount * 0.13)::numeric,2),
       0.13,
       1,
       NOW() - ((g % 120) || ' days')::INTERVAL
FROM generate_series(1,300) g
CROSS JOIN LATERAL (SELECT id, final_amount FROM orders WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND status IN ('APPROVED','COMPLETED') ORDER BY id LIMIT 1 OFFSET (g % 250)) o;

-- =====================================================================
-- 25. 促销 promotions（10：MOQ 5 + FULL_REDUCTION 5）+ 20 promotion_rules
-- =====================================================================
INSERT INTO promotions (tenant_id, code, name, promo_type, priority, valid_from, valid_to, exclusive, status, description, created_by)
SELECT '11111111-1111-1111-1111-111111111111',
       'PM-' || LPAD(g::text,4,'0'),
       CASE WHEN g<=5 THEN '起订量促销-' || g ELSE '满减促销-' || (g-5) END,
       CASE WHEN g<=5 THEN 'MOQ' ELSE 'FULL_REDUCTION' END,
       50 + g,
       NOW() - INTERVAL '30 days', NOW() + INTERVAL '90 days',
       false, 'active',
       '演示促销 ' || g,
       1
FROM generate_series(1,10) g;

INSERT INTO promotion_rules (promotion_id, seq, rule_detail)
SELECT p.id, s,
       CASE WHEN p.promo_type='MOQ'
            THEN jsonb_build_object('threshold_qty', 10*s, 'gift_qty', s)
            ELSE jsonb_build_object('threshold_amount', 10000*s, 'discount', 500*s)
       END
FROM promotions p
CROSS JOIN generate_series(1,2) s
WHERE p.tenant_id='11111111-1111-1111-1111-111111111111';

-- =====================================================================
-- 26. 通知 notifications（100：50 未读 + 50 已读）
-- =====================================================================
INSERT INTO notifications (tenant_id, user_id, channel, title, body, ref_type, ref_id, is_read, created_at)
SELECT '11111111-1111-1111-1111-111111111111',
       (SELECT id FROM users WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 20)),
       (ARRAY['INAPP','WECHAT_BOT','FEISHU_BOT'])[1 + (g % 3)],
       '演示通知 ' || g,
       '这是一条演示通知，内容 ID=' || g,
       (ARRAY['ORDER','CONTRACT','RMA'])[1 + (g % 3)],
       g::text,
       CASE WHEN g<=50 THEN false ELSE true END,
       NOW() - ((g % 60) || ' days')::INTERVAL
FROM generate_series(1,100) g;

-- =====================================================================
-- 27. 审计日志 audit_logs（500：分区表 2026-07~2026-12）
-- =====================================================================
INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, before, after, ip, user_agent, at_time)
SELECT '11111111-1111-1111-1111-111111111111',
       (SELECT id FROM users WHERE tenant_id='11111111-1111-1111-1111-111111111111' ORDER BY id LIMIT 1 OFFSET (g % 20)),
       (ARRAY['CREATE','UPDATE','DELETE','APPROVE','REJECT','LOGIN'])[1 + (g % 6)],
       (ARRAY['ORDER','CONTRACT','DEALER','PRODUCT','USER'])[1 + (g % 5)],
       g::text,
       '{"note":"before"}'::jsonb,
       '{"note":"after"}'::jsonb,
       '127.0.0.1',
       'Mozilla/5.0 DemoAgent',
       TIMESTAMPTZ '2026-07-01 00:00:00+00' + ((g % 180) || ' days')::INTERVAL + ((g % 24) || ' hours')::INTERVAL
FROM generate_series(1,500) g;

DO $$ BEGIN RAISE NOTICE 'V7 Seed 全部数据插入完成'; END $$;