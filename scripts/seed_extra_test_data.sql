-- =====================================================================
-- DMS 演示数据补充脚本 (幂等，测试/生产通用)
-- 覆盖: 合同工作台 / 序列号库存 / 效期预警 / 序列号追溯 / 手术植入报台 /
--       报表订阅 / 产品对码 / 销售岗位可分配账号 / 移动端待审批任务
-- 标识前缀 TDATA- ，可重复执行（重复执行会先清理旧 TDATA 数据再重建）
-- 默认演示租户: 11111111-1111-1111-1111-111111111111
-- 测试: docker exec -i dms-test-postgres psql -U dms -d dms_test -v ON_ERROR_STOP=1 < scripts/seed_extra_test_data.sql
-- 生产: docker exec -i a3493e36ecba_dms-prod-postgres psql -U dms -d dms -v ON_ERROR_STOP=1 < scripts/seed_extra_test_data.sql
-- 首次随 v3.12.5 同步到生产 (2026-08-16)
-- =====================================================================
BEGIN;

DO $$
DECLARE
    v_tid          uuid := '11111111-1111-1111-1111-111111111111';
    v_admin        bigint := 1;
    v_wh           bigint;
    v_dealer       bigint;
    v_hospital     bigint;
    v_p_serial1    bigint;
    v_p_serial2    bigint;
    v_p_normal1    bigint;
    v_contract_tpl bigint;
    v_receipt      bigint;
    v_sout         bigint;
    v_pos          bigint;
    v_node1        bigint;
    v_node2        bigint;
    v_inst         bigint;
    v_task         bigint;
    v_tpl          bigint;
    i              int;
    g              int;
    v_code         text;
    v_serial       text;
    v_batch        text;
    v_statuses     text[] := ARRAY['draft','submitted','effective','expired','terminated'];
    v_categories   text[] := ARRAY['SALES','PURCHASE','CONSIGN','FRAMEWORK','SERVICE'];
    v_biztypes     text[] := ARRAY['SALES_ORDER','PURCHASE_ORDER','CONTRACT','SALES_RETURN','AUTHORIZATION'];
    v_users        bigint[] := ARRAY[30,31,32,33,34,35,36];
    v_unames       text[] := ARRAY['林管理员','赵销售经理','孙销售员','周客服','吴商务','郑财务','王合同专员'];
BEGIN
    SELECT id INTO v_wh FROM warehouses WHERE tenant_id=v_tid ORDER BY id LIMIT 1;
    SELECT id INTO v_dealer FROM dealers WHERE tenant_id=v_tid ORDER BY id LIMIT 1;
    SELECT id INTO v_hospital FROM hospitals WHERE tenant_id=v_tid ORDER BY id LIMIT 1;
    SELECT id INTO v_contract_tpl FROM contract_templates
        WHERE tenant_id=v_tid AND category='SALES' AND deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_p_serial1 FROM products
        WHERE tenant_id=v_tid AND is_serial_managed=true AND deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_p_serial2 FROM products
        WHERE tenant_id=v_tid AND is_serial_managed=true AND deleted_at IS NULL ORDER BY id OFFSET 1 LIMIT 1;
    SELECT id INTO v_p_normal1 FROM products
        WHERE tenant_id=v_tid AND (is_serial_managed IS DISTINCT FROM true) AND deleted_at IS NULL ORDER BY id LIMIT 1;

    -- ---------- 0. 清理旧 TDATA 数据（幂等） ----------
    DELETE FROM approval_records       WHERE tenant_id=v_tid AND comment LIKE 'TDATA-%';
    DELETE FROM approval_tasks         WHERE tenant_id=v_tid AND assignee_name LIKE 'TDATA-%';
    DELETE FROM approval_instances     WHERE tenant_id=v_tid AND title LIKE 'TDATA-%';
    DELETE FROM report_subscription    WHERE tenant_id=v_tid AND name LIKE 'TDATA-%';
    DELETE FROM product_mappings       WHERE manufacturer_product_code LIKE 'TDATA-%' OR dealer_product_code LIKE 'TDATA-%';
    DELETE FROM products              WHERE code LIKE 'TDATA-DLR-%';
    DELETE FROM surgery_report_lines   WHERE report_id IN (SELECT id FROM surgery_reports WHERE tenant_id=v_tid AND code LIKE 'TDATA-SURG-%');
    DELETE FROM surgery_reports        WHERE tenant_id=v_tid AND code LIKE 'TDATA-SURG-%';
    DELETE FROM stock_serials          WHERE tenant_id=v_tid AND serial_no LIKE 'TDATA-SN-%';
    DELETE FROM inventory              WHERE tenant_id=v_tid AND (serial_no LIKE 'TDATA-SN-%' OR batch_no LIKE 'TDATA-BATCH-%');
    DELETE FROM inventory_transactions WHERE tenant_id=v_tid AND (serial_no LIKE 'TDATA-SN-%' OR batch_no LIKE 'TDATA-BATCH-%');
    DELETE FROM receipt_lines          WHERE receipt_id IN (SELECT id FROM receipts WHERE tenant_id=v_tid AND code LIKE 'TDATA-RC-%');
    DELETE FROM receipts               WHERE tenant_id=v_tid AND code LIKE 'TDATA-RC-%';
    DELETE FROM sales_out_lines        WHERE sales_out_id IN (SELECT id FROM sales_outs WHERE tenant_id=v_tid AND code LIKE 'TDATA-SO-%');
    DELETE FROM sales_outs             WHERE tenant_id=v_tid AND code LIKE 'TDATA-SO-%';
    DELETE FROM position_users         WHERE tenant_id=v_tid AND role_type='TDATA';
    DELETE FROM users                 WHERE tenant_id=v_tid AND (username LIKE 'tdata_sales_%' OR username LIKE 'tdata_dealer_%');
    DELETE FROM contracts              WHERE tenant_id=v_tid AND code LIKE 'TDATA-CT-%';

    -- ---------- 1. 合同工作台：15 条，覆盖 5 种状态 ----------
    FOR g IN 1..15 LOOP
        INSERT INTO contracts (tenant_id, code, name, category, application_type, template_id, template_version,
            dealer_id, vendor_party, dealer_party, sign_city, valid_from, valid_to, target_amount, signed_amount,
            payment_terms, settlement_cycle, owner_name, owner_phone, form_data, status,
            submitted_at, effective_at, created_by, updated_by, created_at, updated_at, version)
        VALUES (v_tid, 'TDATA-CT-'||lpad(g::text,4,'0'),
            '演示合同-'||(ARRAY['销售','采购','寄售','框架','服务'])[g%5+1]||'-'||lpad(g::text,4,'0'),
            v_categories[g%5+1], 'NEW', v_contract_tpl, 1,
            v_dealer, 'DMS厂商(上海)有限公司', '演示经销商有限公司',
            (ARRAY['上海','北京','广州','杭州','成都'])[g%5+1],
            current_date - g*5, current_date + 365 - g*5,
            (500000 + g*12000)::numeric(14,2), (120000 + g*8000)::numeric(14,2),
            '月结30天', '月度', '王合同专员', '13800000000',
            jsonb_build_object('products', g*3, 'discount', round((0.85 + g*0.01)::numeric,2)),
            v_statuses[g%5+1],
            CASE WHEN v_statuses[g%5+1] <> 'draft' THEN now()-(g||' days')::interval END,
            CASE WHEN v_statuses[g%5+1] = 'effective' THEN now()-(g||' days')::interval END,
            v_admin, v_admin, now()-(g||' days')::interval, now(), 0);
    END LOOP;

    -- ---------- 2. 序列号库存 + 效期预警 + 序列号追溯 ----------
    -- 2.1 收货单（追溯 RECEIPT 事件）
    v_code := 'TDATA-RC-'||to_char(now(),'YYYYMMDDHH24MISS');
    INSERT INTO receipts (tenant_id, code, receipt_type, ref_doc_type, dealer_id, warehouse_id,
        status, received_at, received_by, remark, created_by, created_at)
    VALUES (v_tid, v_code, 'PURCHASE', 'PURCHASE_ORDER', v_dealer, v_wh,
            'COMPLETED', now() - interval '40 days', v_admin, 'TDATA 序列号追溯演示收货', v_admin, now()-interval '40 days')
    RETURNING id INTO v_receipt;

    -- 2.2 12 个序列号：8 个在库（含过期/近效），4 个已出库（完整时间线）
    v_batch := 'TDATA-BATCH-'||to_char(now()-interval '40 days','YYYYMMDD');
    FOR i IN 1..12 LOOP
        v_serial := 'TDATA-SN-'||lpad(i::text,4,'0');
        INSERT INTO receipt_lines (receipt_id, product_id, batch_no, serial_no,
            prod_date, exp_date, expected_qty, received_qty, created_at)
        VALUES (v_receipt,
            CASE WHEN i<=6 THEN v_p_serial1 ELSE v_p_serial2 END,
            v_batch, v_serial, current_date - 400,
            CASE WHEN i<=2 THEN current_date - 10
                 WHEN i<=5 THEN current_date + 20
                 WHEN i<=9 THEN current_date + 60
                 ELSE current_date + 400 END,
            1, 1, now()-interval '40 days');
        INSERT INTO inventory_transactions (tenant_id, dealer_id, warehouse_id, product_id,
            batch_no, serial_no, qty_change, txn_type, ref_doc_type, ref_doc_id, at_time, operator_id, source_line_id)
        VALUES (v_tid, v_dealer, v_wh,
            CASE WHEN i<=6 THEN v_p_serial1 ELSE v_p_serial2 END,
            v_batch, v_serial, 1, 'RECEIPT', 'RECEIPT', v_receipt,
            now()-interval '40 days', v_admin, v_receipt);
    END LOOP;

    -- 2.3 在库序列号 i=1..8 写入 inventory + stock_serials
    FOR i IN 1..8 LOOP
        v_serial := 'TDATA-SN-'||lpad(i::text,4,'0');
        INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, batch_no, serial_no,
            prod_date, exp_date, qty, in_source, stock_status, created_at, updated_at, version)
        VALUES (v_tid, v_dealer, v_wh,
            CASE WHEN i<=6 THEN v_p_serial1 ELSE v_p_serial2 END,
            v_batch, v_serial, current_date - 400,
            CASE WHEN i<=2 THEN current_date - 10
                 WHEN i<=5 THEN current_date + 20
                 WHEN i<=9 THEN current_date + 60
                 ELSE current_date + 400 END,
            1, 'RECEIPT',
            CASE WHEN i<=2 THEN 'EXPIRED' ELSE 'QUALIFIED' END,
            now()-interval '40 days', now(), 0);
        INSERT INTO stock_serials (tenant_id, warehouse_id, product_id, batch_no, serial_no,
            stock_status, source_doc_type, source_doc_id, source_line_id, received_at)
        VALUES (v_tid, v_wh,
            CASE WHEN i<=6 THEN v_p_serial1 ELSE v_p_serial2 END,
            v_batch, v_serial,
            CASE WHEN i<=2 THEN 'EXPIRED' ELSE 'QUALIFIED' END,
            'RECEIPT', v_receipt, v_receipt, now()-interval '40 days');
    END LOOP;

    -- 2.4 4 个已出库序列号 i=9..12（追溯 SALES_OUT 事件）
    v_code := 'TDATA-SO-'||to_char(now(),'YYYYMMDDHH24MISS');
    INSERT INTO sales_outs (tenant_id, code, dealer_id, terminal_id, business_type, sales_date,
        status, amount_incl_tax, created_by, created_at, updated_at, version)
    VALUES (v_tid, v_code, v_dealer, v_hospital, 'DIRECT', current_date - 5,
            'COMPLETED', 48000::numeric(18,2), v_admin, now()-interval '5 days', now(), 0)
    RETURNING id INTO v_sout;
    FOR i IN 9..12 LOOP
        v_serial := 'TDATA-SN-'||lpad(i::text,4,'0');
        INSERT INTO sales_out_lines (sales_out_id, warehouse_id, product_id, batch_no, serial_no,
            qty, shipped_qty, expected_qty, unit_price, subtotal, seq, created_at)
        VALUES (v_sout, v_wh,
            CASE WHEN i<=6 THEN v_p_serial1 ELSE v_p_serial2 END,
            v_batch, v_serial, 1, 1, 1, 12000::numeric(18,2), 12000::numeric(18,2), i-8, now()-interval '5 days');
        INSERT INTO inventory_transactions (tenant_id, dealer_id, warehouse_id, product_id,
            batch_no, serial_no, qty_change, txn_type, ref_doc_type, ref_doc_id, at_time, operator_id)
        VALUES (v_tid, v_dealer, v_wh,
            CASE WHEN i<=6 THEN v_p_serial1 ELSE v_p_serial2 END,
            v_batch, v_serial, -1, 'SALES_OUT', 'SALES_OUT', v_sout,
            now()-interval '5 days', v_admin);
    END LOOP;

    -- 2.5 非序列号近效期/过期批次库存（填充效期预警列表）
    FOR g IN 1..10 LOOP
        INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, batch_no, serial_no,
            prod_date, exp_date, qty, in_source, stock_status, created_at, updated_at, version)
        VALUES (v_tid, v_dealer, v_wh, v_p_normal1,
            'TDATA-BATCH-EXP-'||lpad(g::text,3,'0'), NULL, current_date - 300,
            CASE WHEN g<=2 THEN current_date - 5
                 WHEN g<=4 THEN current_date + 15
                 WHEN g<=7 THEN current_date + 70
                 ELSE current_date + 300 END,
            (10 + g*3)::numeric(14,4), 'RECEIPT',
            CASE WHEN g<=2 THEN 'EXPIRED' ELSE 'QUALIFIED' END,
            now(), now(), 0);
    END LOOP;

    -- ---------- 3. 手术植入报台：12 条，含产品明细行 ----------
    INSERT INTO surgery_reports (tenant_id, code, dealer_id, terminal_id, warehouse_id, sales_user_id,
        surgery_date, patient_info, doctor_name, status, remark, created_by, created_at, updated_at)
    SELECT v_tid, 'TDATA-SURG-'||lpad(gn::text,4,'0'),
           (SELECT id FROM dealers WHERE tenant_id=v_tid ORDER BY id LIMIT 1 OFFSET ((gn-1)%10)),
           v_hospital, v_wh, 21,
           current_date - (gn-1)*3,
           '患者'||lpad(g::text,3,'0')||'(病案号 TDATA'||lpad(g::text,5,'0')||')',
           (ARRAY['张主任','李主任','王主任','赵主任'])[gn%4+1],
           (ARRAY['DRAFT','SUBMITTED','COMPLETED','COMPLETED','COMPLETED'])[gn%5+1],
           'TDATA 演示手术报台-'||gn,
           v_admin, now()-(gn||' days')::interval, now()
    FROM generate_series(1,12) gn;

    INSERT INTO surgery_report_lines (report_id, product_id, qty, batch_no, serial_no, unit_price, remark, created_at)
    SELECT sr.id,
           CASE WHEN g%2=0 THEN v_p_serial1 ELSE v_p_normal1 END,
           (ARRAY[1,2,1,3])[gn%4+1]::numeric(14,4),
           'TDATA-BATCH-'||to_char(current_date,'YYYYMMDD'),
           CASE WHEN g%2=0 THEN 'TDATA-SN-'||lpad((1+(g%8))::text,4,'0') ELSE NULL END,
           (8000 + gn*120)::numeric(18,2),
           'TDATA 植入物明细行-'||gn, now()
    FROM surgery_reports sr
    CROSS JOIN generate_series(1,2) gn
    WHERE sr.tenant_id=v_tid AND sr.code LIKE 'TDATA-SURG-%';

    -- ---------- 4. 报表订阅：6 条，覆盖主要报表类型与频率 ----------
    INSERT INTO report_subscription (tenant_id, name, report_type, params, cron_expr, emails,
        active, last_run_at, last_status, created_by, created_at, updated_at)
    SELECT v_tid,
           'TDATA-'||(ARRAY['销售排行日报','产品TOP10周报','库存周转月报','订单追溯日报','应收账龄周报','手术统计月报'])[gn],
           (ARRAY['sales-ranking','product-top10','inventory-turnover','order-trace','receivables','surgery-stats'])[gn],
           jsonb_build_object('limit', 50, 'startDate', to_char(current_date-30,'YYYY-MM-DD'),
                              'endDate', to_char(current_date,'YYYY-MM-DD'))::text,
           (ARRAY['DAILY','WEEKLY','MONTHLY','DAILY','WEEKLY','MONTHLY'])[gn],
           'vinkinyu@163.com,sysadmin@dms-test.local',
           true,
           now()-(gn||' days')::interval,
           'SUCCESS', v_admin, now()-(gn||' days')::interval, now()
    FROM generate_series(1,6) gn;

    -- ---------- 5. 产品对码：厂家(default)↔经销商产品映射（active/inactive） ----------
    -- 经销商租户产品很少，先为 DEALER_A1/A2 补充 TDATA 经销商产品，再建立对码
    INSERT INTO products (tenant_id, code, name_cn, spec, unit, current_price, status,
        is_serial_managed, product_type, created_at, updated_at, version)
    SELECT t.tid, 'TDATA-DLR-'||t.code||'-'||lpad(gn::text,3,'0'),
           'TDATA经销商产品-'||t.code||'-'||gn,
           '规格'||gn, '个', (1000 + gn*200)::numeric, 'active',
           (gn%3=0), (CASE WHEN gn%2=0 THEN 'IMPLANT' ELSE 'CONSUMABLE' END),
           now(), now(), 0
    FROM (VALUES ('22222222-0000-0000-0000-000000000001'::uuid,'A1'),
                 ('22222222-0000-0000-0000-000000000002'::uuid,'A2'),
                 ('22222222-0000-0000-0000-000000000003'::uuid,'B1')) t(tid,code)
    CROSS JOIN generate_series(1,6) gn
    ON CONFLICT DO NOTHING;

    INSERT INTO product_mappings (manufacturer_tenant_id, dealer_tenant_id,
        manufacturer_product_id, dealer_product_id, manufacturer_product_code, dealer_product_code,
        package_unit, conversion_rate, status, remark, created_at, updated_at, version)
    SELECT v_tid, dp.tenant_id,
           mp.id, dp.id, mp.code, dp.code,
           'box', (1 + (row_number() OVER (ORDER BY dp.id))*0.5)::numeric,
           CASE WHEN (row_number() OVER (ORDER BY dp.id))%5=0 THEN 'inactive' ELSE 'active' END,
           'TDATA 产品对码演示', now(), now(), 0
    FROM (SELECT id, tenant_id, code, row_number() OVER (ORDER BY id) AS rn FROM products
            WHERE tenant_id=v_tid AND deleted_at IS NULL ORDER BY id LIMIT 12) mp
    JOIN (SELECT id, tenant_id, code, row_number() OVER (ORDER BY id) AS rn FROM products
            WHERE code LIKE 'TDATA-DLR-%' AND deleted_at IS NULL) dp
      ON mp.rn = dp.rn
    ON CONFLICT DO NOTHING;

    -- ---------- 5.5 销售候选账号：为每个四级销售岗位各建 1 个专属销售账号(最多2个/岗) ----------
    -- 密码统一 Sh123456 (bcrypt $2b$10$B3ME3CR.rUd/KKQ1SqzG8.4X02SuVlHY/YZx1JQ9R.MCamsE58Xlu)
    -- 账号随岗位数自动生成(pos 14..29), 一个岗位只挂一个专属销售, 避免一岗多号。
    INSERT INTO users (tenant_id, username, name, user_type, password_hash, must_change_password,
        email, phone, role, sales_user_id, sales_position_id, status, created_at, updated_at, version)
    -- 四级岗位 id 14..29 与 sales_users id 14..29 一一对应; rep 账号直接绑定该 sales_user。
    SELECT v_tid,
           'tdata_rep_'||lower(replace(sp.code,'POS-REP-','')),
           'TDATA销售-'||sp.name, 'EMPLOYEE',
           '$2b$10$B3ME3CR.rUd/KKQ1SqzG8.4X02SuVlHY/YZx1JQ9R.MCamsE58Xlu', false,
           'tdata_rep_'||lower(replace(sp.code,'POS-REP-',''))||'@dms-test.local',
           '139'||lpad(sp.id::text,8,'0'),
           'sales', sp.id, sp.id, 'active', now(), now(), 0
    FROM sales_positions sp
    WHERE sp.tenant_id=v_tid AND sp.level=4 AND sp.deleted_at IS NULL
    ON CONFLICT (tenant_id, username) DO UPDATE
      SET name=EXCLUDED.name, sales_user_id=EXCLUDED.sales_user_id,
          sales_position_id=EXCLUDED.sales_position_id, role='sales', updated_at=now();

    INSERT INTO sales_dealer_mapping (tenant_id, sales_user_id, dealer_id, since_date)
    SELECT v_tid, su.id, su.id, current_date
    FROM sales_users su
    WHERE su.tenant_id=v_tid AND su.id BETWEEN 14 AND 29
    ON CONFLICT DO NOTHING;

    -- 经销商候选账号(role=dealer)：补充 4 个未被占用的经销商绑定
    INSERT INTO users (tenant_id, username, name, user_type, password_hash, must_change_password,
        email, phone, role, dealer_id, status, created_at, updated_at, version)
    SELECT v_tid, 'tdata_dealer_'||gn, 'TDATA经销商账号'||gn, 'DEALER',
           '$2b$10$B3ME3CR.rUd/KKQ1SqzG8.4X02SuVlHY/YZx1JQ9R.MCamsE58Xlu', false,
           'tdata_dealer_'||gn||'@dms-test.local', '138'||lpad(gn::text,8,'0'),
           'dealer', d.id, 'active', now(), now(), 0
    FROM (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM dealers WHERE tenant_id=v_tid ORDER BY id LIMIT 20) d
    CROSS JOIN generate_series(1,1) gn
    WHERE d.rn = gn AND d.id > 4
      AND NOT EXISTS (SELECT 1 FROM users u WHERE u.tenant_id=v_tid AND u.dealer_id=d.id AND u.role='dealer')
    ON CONFLICT DO NOTHING;

    -- ---------- 5.6 销售组织授权：让内置演示销售账号看到自己负责的手术报台 ----------
    -- 数据权限模型: sales_dealer_mapping 上 (tenant_id,dealer_id) 唯一, 一个经销商只归一个销售;
    -- 且后端 SalesOrgResolver 只识别小写 role='sales'。
    -- sales(孙销售员) -> sales_user=14 (负责 dealer 1/17/33/49, 含 TDATA 报台)
    UPDATE users SET sales_user_id=14, role='sales', updated_at=now()
        WHERE tenant_id=v_tid AND username='sales';
    -- sales_mgr(赵销售经理) -> sales_user=1 销售总监 (递归下属覆盖 dealer 1..48, 可见全部报台)
    UPDATE users SET sales_user_id=1, role='sales', updated_at=now()
        WHERE tenant_id=v_tid AND username='sales_mgr';
    -- sys_admin(林管理员/厂商超管) -> role=admin 以全量可见手术报台等所有业务数据
    UPDATE users SET role='admin', updated_at=now()
        WHERE tenant_id=v_tid AND username='sys_admin';
    -- 超管不占用销售岗位
    UPDATE users SET sales_position_id=NULL, updated_at=now() WHERE tenant_id=v_tid AND username='sys_admin';

    -- ---------- 6. 销售岗位绑定：一个岗位最多 1~2 个销售账号, 不再把多人堆到同一岗位 ----------
    -- 6.1 先清理旧版 TDATA 在 position_users 里的一岗多号绑定, 以及误绑的 users.sales_position_id
    DELETE FROM position_users WHERE tenant_id=v_tid AND role_type='TDATA';
    UPDATE users SET sales_position_id=NULL, updated_at=now()
        WHERE tenant_id=v_tid AND (username LIKE 'tdata_sales_%' OR username IN ('contract','fin','biz','cs'));
    -- 旧版 tdata_sales_* 账号已被 tdata_rep_* 取代, 删除以免在候选列表里出现无岗位的孤儿账号
    DELETE FROM users WHERE tenant_id=v_tid AND username LIKE 'tdata_sales_%';

    -- 6.2 每个四级岗位绑定其专属 tdata_rep 账号(1岗1人); 前 4 个岗位再各加 1 个为 2 人
    INSERT INTO position_users (tenant_id, position_id, user_id, role_type, share_ratio, created_at)
    SELECT v_tid, sp.id, u.id, 'TDATA', 100.0::numeric(8,4), now()
    FROM sales_positions sp
    JOIN users u ON u.tenant_id=v_tid
        AND u.username='tdata_rep_'||lower(replace(sp.code,'POS-REP-',''))
    WHERE sp.tenant_id=v_tid AND sp.level=4 AND sp.deleted_at IS NULL
    ON CONFLICT (tenant_id, user_id) DO UPDATE
      SET position_id=EXCLUDED.position_id, role_type='TDATA', share_ratio=EXCLUDED.share_ratio;

    -- 前 4 个岗位补第二个销售(取相邻岗位的 rep, 形成 2 人/岗, 其余仍 1 人/岗)
    INSERT INTO position_users (tenant_id, position_id, user_id, role_type, share_ratio, created_at)
    SELECT v_tid, sp14.id, u_next.id, 'TDATA', 50.0::numeric(8,4), now()
    FROM sales_positions sp14
    JOIN sales_positions sp15 ON sp15.tenant_id=v_tid AND sp15.id=sp14.id+1
    JOIN users u_next ON u_next.tenant_id=v_tid
        AND u_next.username='tdata_rep_'||lower(replace(sp15.code,'POS-REP-',''))
    WHERE sp14.tenant_id=v_tid AND sp14.id=14 AND sp14.deleted_at IS NULL
    ON CONFLICT (tenant_id, user_id) DO NOTHING;

    -- 6.3 同步 users.sales_position_id (候选账号列表按该字段显示已绑定岗位)
    UPDATE users u SET sales_position_id=pu.position_id, updated_at=now()
    FROM position_users pu
    WHERE pu.tenant_id=v_tid AND pu.user_id=u.id AND u.tenant_id=v_tid AND pu.role_type='TDATA';

    -- 6.4 岗位绑定经销商(position_dealers): 确保每个四级岗位至少挂 1 个经销商
    INSERT INTO position_dealers (tenant_id, position_id, dealer_id, created_at)
    SELECT v_tid, sp.id, d.id, now()
    FROM sales_positions sp
    JOIN dealers d ON d.tenant_id=v_tid AND d.id = sp.id
    WHERE sp.tenant_id=v_tid AND sp.level=4 AND sp.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM position_dealers pd WHERE pd.tenant_id=v_tid AND pd.position_id=sp.id)
    ON CONFLICT DO NOTHING;

    -- ---------- 7. 移动端待审批任务：7 条 PENDING，覆盖各业务类型 ----------
    -- 用一个已启用的采购订单审批模板快照构造实例（不依赖真实业务单据，避免触发引擎副作用）
    SELECT id INTO v_tpl FROM approval_templates
        WHERE tenant_id=v_tid AND business_type='PURCHASE_ORDER' AND status='ENABLED' ORDER BY id LIMIT 1;

    FOR i IN 1..7 LOOP
        INSERT INTO approval_instances (tenant_id, template_id, template_version_no, business_type,
            business_id, business_code, title, submitter_id, submitter_name, status,
            current_node_name, reject_policy, business_snapshot, started_at, created_at, updated_at, version)
        VALUES (v_tid, v_tpl, 1, v_biztypes[(i%5)+1],
            900000 + i, 'TDATA-BIZ-'||lpad(i::text,4,'0'),
            'TDATA-'||(ARRAY['销售订单','采购订单','合同','销售退货','授权'])[(i%5)+1]||'审批-'||i,
            32, '孙销售员', 'RUNNING', '部门负责人审批',
            'RETURN_TO_SUBMITTER',
            jsonb_build_object('amount', 10000+i*1500, 'applicant', '孙销售员', 'tdata', true),
            now() - (i||' hours')::interval, now() - (i||' hours')::interval, now(), 0)
        RETURNING id INTO v_inst;

        INSERT INTO approval_tasks (instance_id, tenant_id, node_name, assignee_id, assignee_name,
            task_type, status, approve_mode, due_at, created_at, updated_at, version)
        VALUES (v_inst, v_tid, '部门负责人审批', v_users[i], 'TDATA-'||v_unames[i],
            'NORMAL', 'PENDING', 'ANY', now() + interval '3 days',
            now() - (i||' hours')::interval, now(), 0);
    END LOOP;

    -- ---------- 完成 ----------
    RAISE NOTICE 'TDATA seed completed for tenant %', v_tid;
END $$;

COMMIT;

-- ---------- 结果核对 ----------
SELECT 'contracts' AS t, count(*) FROM contracts WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND code LIKE 'TDATA-CT-%'
UNION ALL SELECT 'stock_serials', count(*) FROM stock_serials WHERE serial_no LIKE 'TDATA-SN-%'
UNION ALL SELECT 'inventory(serial/exp)', count(*) FROM inventory WHERE serial_no LIKE 'TDATA-SN-%' OR batch_no LIKE 'TDATA-BATCH-%'
UNION ALL SELECT 'surgery_reports', count(*) FROM surgery_reports WHERE code LIKE 'TDATA-SURG-%'
UNION ALL SELECT 'report_subscription', count(*) FROM report_subscription WHERE name LIKE 'TDATA-%'
UNION ALL SELECT 'product_mappings', count(*) FROM product_mappings WHERE manufacturer_product_code LIKE 'TDATA-%'
UNION ALL SELECT 'position_users(TDATA)', count(*) FROM position_users WHERE role_type='TDATA'
UNION ALL SELECT 'approval_tasks(PENDING,TDATA)', count(*) FROM approval_tasks WHERE assignee_name LIKE 'TDATA-%' AND status='PENDING';
