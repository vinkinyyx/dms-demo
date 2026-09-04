-- V148: v4.6.3 审批流补齐（需求7）。
-- 测试环境口径：所有审批流均由「提交人本人」或「系统管理员」审批，审批一层（单节点 ANY 或签）。
-- 1) 新增 6 条默认租户手动审批模板：合同终止、授权终止、授权续约、物料创建、经销商创建、供应商创建。
--    （合同创建 CONTRACT / 授权创建 AUTHORIZATION / 销售订单 SALES_ORDER / 销退 SALES_RETURN 等模板历史版本已播种）
-- 2) 为默认租户下所有已启用模板的节点幂等补挂 SUBMITTER(提交人本人, ref_id=0) 审批人；
--    ROLE#1(系统管理员) 历史已播种，故每个节点最终为 提交人本人 ∪ 系统管理员 或签。
DO $$
DECLARE
    v_tid UUID := '11111111-1111-1111-1111-111111111111';
    v_tpl BIGINT;
    v_node BIGINT;
BEGIN
    -- ========== 一、新增 6 条模板 ==========
    -- 1. 合同终止
    IF NOT EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'CONTRACT_TERMINATE' AND status = 'ENABLED') THEN
        INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
        VALUES (v_tid, 'CONTRACT_TERMINATE', 'CONTRACT-TERMINATE-DEFAULT', '合同终止默认审批模板', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, '已生效合同发起终止的审批（通过后合同置 terminated，驳回/撤回/退回恢复 effective）', now(), now(), now())
        RETURNING id INTO v_tpl;
        INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
        VALUES (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_node, v_tid, 'SUBMITTER', 0, '提交人本人', now()),
               (v_node, v_tid, 'ROLE', 1, '系统管理员', now());
    END IF;

    -- 2. 授权终止
    IF NOT EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'AUTHORIZATION_TERMINATE' AND status = 'ENABLED') THEN
        INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
        VALUES (v_tid, 'AUTHORIZATION_TERMINATE', 'AUTHZ-TERMINATE-DEFAULT', '授权终止默认审批模板', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, '有效/未开始授权发起终止的审批（通过后授权置 terminated，驳回/撤回/退回恢复原状态）', now(), now(), now())
        RETURNING id INTO v_tpl;
        INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
        VALUES (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_node, v_tid, 'SUBMITTER', 0, '提交人本人', now()),
               (v_node, v_tid, 'ROLE', 1, '系统管理员', now());
    END IF;

    -- 3. 授权续约
    IF NOT EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'AUTHORIZATION_RENEW' AND status = 'ENABLED') THEN
        INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
        VALUES (v_tid, 'AUTHORIZATION_RENEW', 'AUTHZ-RENEW-DEFAULT', '授权续约默认审批模板', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, '有效/未开始授权续约生成新授权的审批（通过后新授权生效，驳回/撤回/退回回草稿）', now(), now(), now())
        RETURNING id INTO v_tpl;
        INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
        VALUES (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_node, v_tid, 'SUBMITTER', 0, '提交人本人', now()),
               (v_node, v_tid, 'ROLE', 1, '系统管理员', now());
    END IF;

    -- 4. 物料（产品）创建
    IF NOT EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'PRODUCT_CREATE' AND status = 'ENABLED') THEN
        INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
        VALUES (v_tid, 'PRODUCT_CREATE', 'PRODUCT-CREATE-DEFAULT', '物料创建默认审批模板', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, '新建物料/产品的审批（通过后物料生效可被选择引用，驳回/撤回/退回回草稿，待审期间不进选择器）', now(), now(), now())
        RETURNING id INTO v_tpl;
        INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
        VALUES (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_node, v_tid, 'SUBMITTER', 0, '提交人本人', now()),
               (v_node, v_tid, 'ROLE', 1, '系统管理员', now());
    END IF;

    -- 5. 经销商创建
    IF NOT EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'DEALER_CREATE' AND status = 'ENABLED') THEN
        INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
        VALUES (v_tid, 'DEALER_CREATE', 'DEALER-CREATE-DEFAULT', '经销商创建默认审批模板', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, '新建经销商的审批（通过后经销商生效可被选择引用，驳回/撤回/退回回草稿，待审期间不进选择器）', now(), now(), now())
        RETURNING id INTO v_tpl;
        INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
        VALUES (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_node, v_tid, 'SUBMITTER', 0, '提交人本人', now()),
               (v_node, v_tid, 'ROLE', 1, '系统管理员', now());
    END IF;

    -- 6. 供应商创建
    IF NOT EXISTS (SELECT 1 FROM approval_templates WHERE tenant_id = v_tid AND business_type = 'SUPPLIER_CREATE' AND status = 'ENABLED') THEN
        INSERT INTO approval_templates (tenant_id, business_type, code, name, version_no, template_type, status, priority, reject_policy, timeout_hours, remind_interval_hours, max_remind_count, description, published_at, created_at, updated_at)
        VALUES (v_tid, 'SUPPLIER_CREATE', 'SUPPLIER-CREATE-DEFAULT', '供应商创建默认审批模板', 1, 'MANUAL', 'ENABLED', 10, 'RETURN_TO_SUBMITTER', 48, 24, 3, '新建供应商的审批（通过后供应商生效可被选择引用，驳回/撤回/退回回草稿，待审期间不进选择器）', now(), now(), now())
        RETURNING id INTO v_tpl;
        INSERT INTO approval_template_nodes (template_id, tenant_id, node_order, name, approve_mode, allow_transfer, allow_add_sign, timeout_hours, remind_interval_hours, max_remind_count, created_at, updated_at)
        VALUES (v_tpl, v_tid, 1, '审批', 'ANY', true, true, 48, 24, 3, now(), now()) RETURNING id INTO v_node;
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_node, v_tid, 'SUBMITTER', 0, '提交人本人', now()),
               (v_node, v_tid, 'ROLE', 1, '系统管理员', now());
    END IF;

    -- ========== 二、为历史已启用模板的所有节点幂等补挂「提交人本人」审批人 ==========
    INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
    SELECT n.id, n.tenant_id, 'SUBMITTER', 0, '提交人本人', now()
    FROM approval_template_nodes n
    JOIN approval_templates t ON t.id = n.template_id
    WHERE t.tenant_id = v_tid
      AND t.status = 'ENABLED'
      AND NOT EXISTS (
          SELECT 1 FROM approval_node_assignees a
          WHERE a.node_id = n.id AND a.assignee_type = 'SUBMITTER'
      );
END $$;
