-- V137: v4.4.0 开票订单（order_type=INVOICE，业务类型 INVOICE_ORDER）接入审批流。
-- 开票订单提交即预占寄售库存；审批通过正式扣减，驳回/退回/撤回释放预占。
-- 此处为默认租户播种 INVOICE_ORDER 手动审批模板（ROLE#1 系统管理员）。
DO $$
DECLARE
    v_tid UUID := '11111111-1111-1111-1111-111111111111';
    v_tpl BIGINT;
    v_node BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'INVOICE_ORDER' AND status = 'ENABLED') THEN
        RETURN;
    END IF;

    INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
    VALUES (v_tid, 'INVOICE_ORDER', 'INVOICE-DEFAULT', E'\u5f00\u7968\u8ba2\u5355\u9ed8\u8ba4\u5ba1\u6279\u6a21\u677f', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, E'\u5f00\u7968\u8ba2\u5355\u5ba1\u6279\uff08\u9488\u5bf9\u7ecf\u9500\u5546\u5bc4\u552e\u5e93\u5b58\u5f00\u7968\uff1b\u901a\u8fc7\u540e\u5b9e\u6263\u5bc4\u552e\u5e93\u5b58\uff0c\u9a73\u56de/\u9000\u56de/\u64a4\u56de\u91ca\u653e\u9884\u5360\uff09', now(), now(), now())
    RETURNING id INTO v_tpl;

    INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
    VALUES (v_tpl, v_tid, 1, E'\u5ba1\u6279', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;

    INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
    VALUES (v_node, v_tid, 'ROLE', 1, E'\u7cfb\u7edf\u7ba1\u7406\u5458', now());
END $$;
