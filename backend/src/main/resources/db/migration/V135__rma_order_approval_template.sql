-- V135: RMA 销退单（business_type=RMA_ORDER）接入审批流。
-- 为默认租户播种 RMA_ORDER 手动审批模板（单节点：ROLE#1 系统管理员）。
-- 幂等：已存在启用模板时跳过，便于校验和对齐后重复执行。
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
    VALUES (v_tid, 'RMA_ORDER', 'RMA-DEFAULT', '销退单默认审批模板', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, 'RMA 销退单审批（审批通过后回写库存、释放可退锁定）', now(), now(), now())
    RETURNING id INTO v_tpl;

    INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
    VALUES (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;

    INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
    VALUES (v_node, v_tid, 'ROLE', 1, '系统管理员', now());
END $$;
