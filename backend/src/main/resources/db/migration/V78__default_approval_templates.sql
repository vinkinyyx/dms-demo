-- V78: seed default approval templates for the remaining 5 business types
-- Each template has a single SYS_ADMIN approval node, ANY mode, 48h timeout, 24h reminder, max 3.
DO $$
DECLARE
    v_tid UUID := '11111111-1111-1111-1111-111111111111';
    v_tpl BIGINT;
    v_node BIGINT;
    v_rec RECORD;
BEGIN
    FOR v_rec IN (VALUES
        ('PURCHASE_ORDER'::VARCHAR(64), 'PO'::VARCHAR(64), E'\u91c7\u8d2d\u8ba2\u5355\u9ed8\u8ba4\u5ba1\u6279\u6a21\u677f'::VARCHAR(200)),
        ('SALES_RETURN'::VARCHAR(64), 'SRT'::VARCHAR(64), E'\u9500\u552e\u9000\u8d27\u9ed8\u8ba4\u5ba1\u6279\u6a21\u677f'::VARCHAR(200)),
        ('PURCHASE_RETURN'::VARCHAR(64), 'PRT'::VARCHAR(64), E'\u91c7\u8d2d\u9000\u8d27\u9ed8\u8ba4\u5ba1\u6279\u6a21\u677f'::VARCHAR(200)),
        ('CONTRACT'::VARCHAR(64), 'CT'::VARCHAR(64), E'\u5408\u540c\u9ed8\u8ba4\u5ba1\u6279\u6a21\u677f'::VARCHAR(200)),
        ('AUTHORIZATION'::VARCHAR(64), 'AUTH'::VARCHAR(64), E'\u6388\u6743\u9ed8\u8ba4\u5ba1\u6279\u6a21\u677f'::VARCHAR(200))
    ) LOOP
        IF EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id=v_tid AND business_type=v_rec.column1 AND status='ENABLED') THEN
            CONTINUE;
        END IF;
        INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
        VALUES (v_tid, v_rec.column1, v_rec.column2 || '-DEFAULT', v_rec.column3, 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, v_rec.column3, now(), now(), now())
        RETURNING id INTO v_tpl;
        INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
        VALUES (v_tpl, v_tid, 1, E'\u5ba1\u6279', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_node, v_tid, 'ROLE', 1, E'\u7cfb\u7edf\u7ba1\u7406\u5458', now());
    END LOOP;
END $$;
