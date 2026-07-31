-- V34: 清理孤儿收货入库 + 重造 4 种场景 (DMS v3.7.3, R9)
-- Author: DMS Fix Bot @ 2026-07-26
-- 目标：清理旧无用测试数据 + 重造 4 类关联 PO 的收货入库
-- 覆盖：仅对 tenant.code='default' 生效；PO 单号全局唯一，避免多租户冲突

DO $BODY$
DECLARE
    v_tid uuid;
    v_wh_id bigint;
    v_supplier_id bigint;
    v_p1 bigint;
    v_p2 bigint;
    v_po_id bigint;
    v_rc_id bigint;
BEGIN
    SELECT id INTO v_tid FROM tenants WHERE code = 'default' LIMIT 1;
    IF v_tid IS NULL THEN
        RAISE NOTICE 'V34 skip: default tenant not present';
        RETURN;
    END IF;

    SELECT id INTO v_wh_id FROM warehouses WHERE tenant_id = v_tid ORDER BY id LIMIT 1;
    SELECT id INTO v_supplier_id FROM suppliers WHERE tenant_id = v_tid AND deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_p1 FROM products WHERE tenant_id = v_tid ORDER BY id LIMIT 1;
    SELECT id INTO v_p2 FROM products WHERE tenant_id = v_tid ORDER BY id OFFSET 1 LIMIT 1;
    IF v_p2 IS NULL THEN v_p2 := v_p1; END IF;

    IF v_wh_id IS NULL OR v_supplier_id IS NULL OR v_p1 IS NULL THEN
        RAISE NOTICE 'V34 skip: missing base data (warehouse/supplier/product)';
        RETURN;
    END IF;

    -- 1) 清理旧的 seed 收货 (RC-* / 无 PO 关联) 与本次 V373 幂等
    DELETE FROM receipt_lines WHERE receipt_id IN (
        SELECT id FROM receipts WHERE tenant_id = v_tid AND (source_po_id IS NULL OR code LIKE 'RC-%')
    );
    DELETE FROM receipts WHERE tenant_id = v_tid AND (source_po_id IS NULL OR code LIKE 'RC-%');

    DELETE FROM receipt_lines WHERE receipt_id IN (
        SELECT r.id FROM receipts r JOIN purchase_orders po ON po.id = r.source_po_id
        WHERE po.tenant_id = v_tid AND po.code LIKE 'PO-V373-%'
    );
    DELETE FROM receipts WHERE source_po_id IN (
        SELECT id FROM purchase_orders WHERE tenant_id = v_tid AND code LIKE 'PO-V373-%'
    );
    DELETE FROM purchase_order_lines WHERE po_id IN (
        SELECT id FROM purchase_orders WHERE tenant_id = v_tid AND code LIKE 'PO-V373-%'
    );
    DELETE FROM purchase_orders WHERE tenant_id = v_tid AND code LIKE 'PO-V373-%';

    -- Scene A_COMPLETED
    INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, warehouse_id, amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, submitted_at, approved_at, approved_by, completed_at, created_at, updated_at, created_by) VALUES (v_tid, 'PO-V373-A', 'NORMAL', v_supplier_id, v_wh_id, 1300.00, 0.00, 1300.00, 169.00, CURRENT_DATE + 3, 'COMPLETED', 'V3.7.3 场景A 已完成', now() - interval '4 days', now() - interval '3 days', 1, now() - interval '1 day', now() - interval '5 days', now() - interval '1 day', 1) RETURNING id INTO v_po_id;
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 1, v_p1, 10, 0, 50.0000, 0.13, 500.00);
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 2, v_p2, 20, 0, 30.0000, 0.13, 600.00);
    INSERT INTO receipts (tenant_id, code, ref_doc_type, ref_doc_id, warehouse_id, status, auto_created, source_po_id, received_at, remark, created_at, updated_at) VALUES (v_tid, 'RK-V373-A', 'purchase_order', v_po_id, v_wh_id, 'COMPLETED', true, v_po_id, now() - interval '1 day', 'V3.7.3 场景A 收货完成', now() - interval '3 days', now() - interval '1 day') RETURNING id INTO v_rc_id;
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p1, 10, 10, 0);
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p2, 20, 20, 0);

    -- Scene B_PARTIAL
    INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, warehouse_id, amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, submitted_at, approved_at, approved_by, completed_at, created_at, updated_at, created_by) VALUES (v_tid, 'PO-V373-B', 'NORMAL', v_supplier_id, v_wh_id, 1300.00, 0.00, 1300.00, 169.00, CURRENT_DATE + 3, 'RECEIVING', 'V3.7.3 场景B 部分收货', now() - interval '4 days', now() - interval '3 days', 1, NULL, now() - interval '5 days', now() - interval '1 day', 1) RETURNING id INTO v_po_id;
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 1, v_p1, 10, 0, 50.0000, 0.13, 500.00);
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 2, v_p2, 20, 0, 30.0000, 0.13, 600.00);
    INSERT INTO receipts (tenant_id, code, ref_doc_type, ref_doc_id, warehouse_id, status, auto_created, source_po_id, received_at, remark, created_at, updated_at) VALUES (v_tid, 'RK-V373-B', 'purchase_order', v_po_id, v_wh_id, 'PARTIAL_RECEIVED', true, v_po_id, now() - interval '1 day', 'V3.7.3 场景B 部分收货', now() - interval '3 days', now() - interval '1 day') RETURNING id INTO v_rc_id;
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p1, 10, 6, 0);
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p2, 20, 8, 0);

    -- Scene C_DRAFT
    INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, warehouse_id, amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, submitted_at, approved_at, approved_by, completed_at, created_at, updated_at, created_by) VALUES (v_tid, 'PO-V373-C', 'NORMAL', v_supplier_id, v_wh_id, 1300.00, 0.00, 1300.00, 169.00, CURRENT_DATE + 3, 'APPROVED', 'V3.7.3 场景C 收货未开始', now() - interval '4 days', now() - interval '3 days', 1, NULL, now() - interval '5 days', now() - interval '1 day', 1) RETURNING id INTO v_po_id;
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 1, v_p1, 10, 0, 50.0000, 0.13, 500.00);
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 2, v_p2, 20, 0, 30.0000, 0.13, 600.00);
    INSERT INTO receipts (tenant_id, code, ref_doc_type, ref_doc_id, warehouse_id, status, auto_created, source_po_id, received_at, remark, created_at, updated_at) VALUES (v_tid, 'RK-V373-C', 'purchase_order', v_po_id, v_wh_id, 'DRAFT', true, v_po_id, NULL, 'V3.7.3 场景C 收货入库草稿', now() - interval '3 days', now() - interval '1 day') RETURNING id INTO v_rc_id;
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p1, 10, 0, 0);
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p2, 20, 0, 0);

    -- Scene D_CANCELLED
    INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, warehouse_id, amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, submitted_at, approved_at, approved_by, completed_at, created_at, updated_at, created_by) VALUES (v_tid, 'PO-V373-D', 'NORMAL', v_supplier_id, v_wh_id, 1300.00, 0.00, 1300.00, 169.00, CURRENT_DATE + 3, 'APPROVED', 'V3.7.3 场景D 已取消收货', now() - interval '4 days', now() - interval '3 days', 1, NULL, now() - interval '5 days', now() - interval '1 day', 1) RETURNING id INTO v_po_id;
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 1, v_p1, 10, 0, 50.0000, 0.13, 500.00);
    INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal) VALUES (v_po_id, 2, v_p2, 20, 0, 30.0000, 0.13, 600.00);
    INSERT INTO receipts (tenant_id, code, ref_doc_type, ref_doc_id, warehouse_id, status, auto_created, source_po_id, received_at, remark, created_at, updated_at) VALUES (v_tid, 'RK-V373-D', 'purchase_order', v_po_id, v_wh_id, 'CANCELLED', true, v_po_id, NULL, 'V3.7.3 场景D 已取消', now() - interval '3 days', now() - interval '1 day') RETURNING id INTO v_rc_id;
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p1, 10, 0, 10);
    INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, cancelled_qty) VALUES (v_rc_id, v_p2, 20, 0, 20);

END $BODY$;

