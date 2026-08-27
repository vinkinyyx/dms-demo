-- V135: RMA 销退单（v4.3.0 多出库销退 rma_orders）接入审批流。
-- 此前 RmaOrderService.submit/create 仅置 SUBMITTED 并锁定可退量，未创建审批实例，
-- 导致销退单提交后审批中心无待办。此处为默认租户播种 RMA_ORDER 手动审批模板（ROLE#1 系统管理员）。
DO $$
DECLARE
    v_tid UUID := '11111111-1111-1111-1111-111111111111';
    v_tpl BIGINT;
    v_node BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'RMA_ORDER' AND status = 'ENABLED') THEN
        RETURN;
    END IF;

    INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
    VALUES (v_tid, 'RMA_ORDER', 'RMA-DEFAULT', E'\u9500\u9000\u5355\u9ed8\u8ba4\u5ba1\u6279\u6a21\u677f', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, E'RMA \u9500\u9000\u5355\u5ba1\u6279\uff08\u5ba1\u6279\u901a\u8fc7\u540e\u56de\u5199\u5e93\u5b58\u3001\u91ca\u653e\u53ef\u9000\u9501\u5b9a\uff09', now(), now(), now())
    RETURNING id INTO v_tpl;

    INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
    VALUES (v_tpl, v_tid, 1, E'\u5ba1\u6279', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;

    INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
    VALUES (v_node, v_tid, 'ROLE', 1, E'\u7cfb\u7edf\u7ba1\u7406\u5458', now());
END $$;
