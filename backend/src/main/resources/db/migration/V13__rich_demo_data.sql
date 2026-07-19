-- ============================================================
-- V13: 丰富测试数据（订单/收货/库存/报台/近12月历史）
-- ============================================================

DO $$
DECLARE
    v_tenant UUID := '11111111-1111-1111-1111-111111111111';
    v_dealer_ids BIGINT[];
    v_product_ids BIGINT[];
    v_hospital_ids BIGINT[];
    v_wh_id BIGINT;
    i INT;
    v_dealer BIGINT;
    v_product BIGINT;
    v_hosp BIGINT;
    v_month_offset INT;
    v_created TIMESTAMP;
    v_order_id BIGINT;
    v_po_id BIGINT;
    v_qty NUMERIC;
    v_price NUMERIC;
    v_amount NUMERIC;
    v_status TEXT;
    v_status_list TEXT[] := ARRAY['DRAFT','SUBMITTED','APPROVED','APPROVED','APPROVED','COMPLETED','COMPLETED','CANCELLED'];
BEGIN
    -- 收集 ID 池
    SELECT ARRAY_AGG(id) INTO v_dealer_ids FROM dealers WHERE tenant_id = v_tenant LIMIT 30;
    SELECT ARRAY_AGG(id) INTO v_product_ids FROM products WHERE tenant_id = v_tenant LIMIT 50;
    SELECT ARRAY_AGG(id) INTO v_hospital_ids FROM hospitals WHERE tenant_id = v_tenant LIMIT 20;
    SELECT id INTO v_wh_id FROM warehouses WHERE tenant_id = v_tenant LIMIT 1;

    IF v_dealer_ids IS NULL OR array_length(v_dealer_ids, 1) IS NULL THEN RETURN; END IF;

    -- 检查是否已注入过（幂等）
    IF (SELECT COUNT(*) FROM orders WHERE code LIKE 'DEMO-ORD-%') > 100 THEN
        RAISE NOTICE 'v3.4.4 测试数据已存在，跳过';
        RETURN;
    END IF;

    -- 生成 200 个订单（分布在近 12 个月）
    FOR i IN 1..200 LOOP
        v_dealer := v_dealer_ids[1 + (i % COALESCE(array_length(v_dealer_ids,1),1))];
        v_product := v_product_ids[1 + (i % COALESCE(array_length(v_product_ids,1),1))];
        v_month_offset := (i % 12);
        v_created := (now() - (v_month_offset || ' months')::interval - (i || ' hours')::interval);
        v_qty := 1 + (i % 20);
        v_price := 100 + (i % 500);
        v_amount := v_qty * v_price;
        v_status := v_status_list[1 + (i % array_length(v_status_list,1))];

        INSERT INTO orders (tenant_id, code, order_type, dealer_id, is_red,
                           amount_incl_tax, discount_amount, final_amount,
                           status, expected_date, remark,
                           created_at, updated_at, submitted_at, approved_at)
        VALUES (v_tenant, 'DEMO-ORD-' || LPAD(i::text, 5, '0'),
                CASE WHEN i%4=0 THEN 'EMERGENCY' WHEN i%3=0 THEN 'SHORTAGE' ELSE 'NORMAL' END,
                v_dealer, false,
                v_amount, v_amount * 0.02, v_amount * 0.98,
                v_status, (v_created + '15 days'::interval)::date,
                '批量测试数据',
                v_created, v_created, v_created + '1 hour'::interval,
                CASE WHEN v_status IN ('APPROVED','COMPLETED') THEN v_created + '2 hours'::interval ELSE NULL END)
        RETURNING id INTO v_order_id;

        -- 明细 1-3 行
        INSERT INTO order_lines (order_id, product_id, qty, unit_price, sub_total, seq)
        VALUES (v_order_id, v_product, v_qty, v_price, v_amount, 1);
        IF i % 3 = 0 THEN
            INSERT INTO order_lines (order_id, product_id, qty, unit_price, sub_total, seq)
            VALUES (v_order_id, v_product_ids[1 + ((i+7) % COALESCE(array_length(v_product_ids,1),1))],
                    v_qty / 2, v_price, v_price * v_qty / 2, 2);
        END IF;
    END LOOP;

    -- 生成 50 个采购订单
    FOR i IN 1..50 LOOP
        v_month_offset := (i % 6);
        v_created := (now() - (v_month_offset || ' months')::interval - (i || ' hours')::interval);
        v_qty := 10 + (i % 50);
        v_price := 50 + (i % 200);
        v_status := v_status_list[1 + (i % array_length(v_status_list,1))];

        INSERT INTO purchase_orders (tenant_id, code, is_red, warehouse_id,
                                     amount_incl_tax, discount_amount, final_amount, tax_amount,
                                     status, expected_date, remark, created_at, updated_at)
        VALUES (v_tenant, 'DEMO-PO-' || LPAD(i::text, 5, '0'), false, v_wh_id,
                v_qty * v_price, 0, v_qty * v_price, v_qty * v_price * 0.13,
                v_status, (v_created + '15 days'::interval)::date,
                '采购测试数据', v_created, v_created)
        RETURNING id INTO v_po_id;

        INSERT INTO purchase_order_lines (po_id, product_id, qty, unit_price, subtotal, seq)
        VALUES (v_po_id, v_product_ids[1 + (i % COALESCE(array_length(v_product_ids,1),1))],
                v_qty, v_price, v_qty * v_price, 1);
    END LOOP;

    -- 生成 30 个手术报台
    IF v_hospital_ids IS NOT NULL AND array_length(v_hospital_ids,1) > 0 THEN
        FOR i IN 1..30 LOOP
            v_dealer := v_dealer_ids[1 + (i % COALESCE(array_length(v_dealer_ids,1),1))];
            v_product := v_product_ids[1 + (i % COALESCE(array_length(v_product_ids,1),1))];
            v_hosp := v_hospital_ids[1 + (i % COALESCE(array_length(v_hospital_ids,1),1))];
            v_created := (now() - ((i % 90) || ' days')::interval);

            DECLARE v_sr_id BIGINT; BEGIN
                INSERT INTO surgery_reports (tenant_id, code, dealer_id, terminal_id, warehouse_id,
                                             surgery_date, patient_info, doctor_name, status,
                                             created_at, updated_at)
                VALUES (v_tenant, 'DEMO-SURG-' || LPAD(i::text, 5, '0'),
                        v_dealer, v_hosp, v_wh_id,
                        v_created::date, '患者' || i || '号，' || (30 + i%50) || '岁', '主刀医生-张' || (i%20),
                        'COMPLETED', v_created, v_created)
                RETURNING id INTO v_sr_id;

                INSERT INTO surgery_report_lines (report_id, product_id, qty, batch_no, unit_price)
                VALUES (v_sr_id, v_product, 1, 'BATCH-DEMO-' || LPAD((i%10)::text,4,'0'), 500 + i*10);
            END;
        END LOOP;
    END IF;

    RAISE NOTICE 'v3.4.4 已注入 200 订单/50 采购单/30 手术报台';
END $$;
