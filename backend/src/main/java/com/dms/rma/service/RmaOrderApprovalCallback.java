/*
 * RMA 销退单审批回调（业务类型 RMA_ORDER）。
 * - 审批通过：RMA 单状态置 COMPLETED 并按来源出库行回写库存/可退量（RmaOrderService.complete）。
 * - 审批驳回：状态 REJECTED，可重新编辑提交。
 * - 退回/撤回/终止为草稿：释放本次锁定的可退数量，状态回 DRAFT。
 */
package com.dms.rma.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RmaOrderApprovalCallback implements ApprovalBusinessCallback {

    public static final String BUSINESS_TYPE = "RMA_ORDER";

    private final EntityManager em;
    @Lazy
    private final RmaOrderService rmaOrderService;

    @Override
    public boolean supports(String businessType) {
        return BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void onApproved(ApprovalInstance instance) {
        // RmaOrderService.startApproval 已在 AUTO_APPROVED 场景直接置 COMPLETED；
        // 手动审批通过时在此完成库存回写（complete 内部幂等：已 COMPLETED 直接返回）。
        try {
            rmaOrderService.complete(instance.getBusinessId());
        } catch (Exception e) {
            log.error("RMA 销退单 {} 审批通过回写失败: {}", instance.getBusinessId(), e.getMessage(), e);
            throw e;
        }
        writeOpLog(instance, "APPROVE", "销退单-审批通过");
    }

    @Override
    @Transactional
    public void onReturned(ApprovalInstance instance) {
        releaseLocksAndDraft(instance);
        writeOpLog(instance, "UPDATE", "销退单-审批退回");
    }

    @Override
    @Transactional
    public void onRejected(ApprovalInstance instance) {
        releaseLocks(instance);
        em.createNativeQuery("UPDATE rma_orders SET status='REJECTED', updated_at=now() WHERE id=?1 AND tenant_id=?2 AND deleted_at IS NULL")
                .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        writeOpLog(instance, "REJECT", "销退单-审批驳回");
    }

    @Override
    @Transactional
    public void onWithdrawn(ApprovalInstance instance) {
        releaseLocksAndDraft(instance);
        writeOpLog(instance, "UPDATE", "销退单-撤回审批");
    }

    @Override
    @Transactional
    public void onTerminated(ApprovalInstance instance, String result) {
        if ("DRAFT".equalsIgnoreCase(result)) {
            releaseLocksAndDraft(instance);
        } else {
            releaseLocks(instance);
            em.createNativeQuery("UPDATE rma_orders SET status='CANCELLED', updated_at=now() WHERE id=?1 AND tenant_id=?2 AND deleted_at IS NULL")
                    .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        }
        writeOpLog(instance, "UPDATE", "销退单-审批终止");
    }

    private void releaseLocksAndDraft(ApprovalInstance instance) {
        releaseLocks(instance);
        em.createNativeQuery("UPDATE rma_orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2 AND deleted_at IS NULL")
                .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
    }

    /** 释放本张 RMA 单在 sales_out_lines 上锁定的可退数量（按 rma_order_lines 记录逐行回退）。 */
    private void releaseLocks(ApprovalInstance instance) {
        em.createNativeQuery(
                "UPDATE sales_out_lines sol SET return_locked_qty = GREATEST(COALESCE(sol.return_locked_qty,0) - l.qty, 0) " +
                "FROM rma_order_lines l " +
                "WHERE l.rma_id = ?1 AND l.sales_out_line_id = sol.id")
                .setParameter(1, instance.getBusinessId()).executeUpdate();
    }

    private void writeOpLog(ApprovalInstance instance, String action, String remark) {
        try {
            em.createNativeQuery("INSERT INTO operation_log (tenant_code,business_type,business_id,operator_id,operator_name,action,remark,created_at,updated_at) VALUES (?1,?2,?3,?4,?5,?6,?7,now(),now())")
                    .setParameter(1, instance.getTenantId() == null ? "default" : instance.getTenantId().toString())
                    .setParameter(2, "rmaOrder")
                    .setParameter(3, instance.getBusinessId())
                    .setParameter(4, TenantContext.getUserId())
                    .setParameter(5, TenantContext.getUsername())
                    .setParameter(6, action)
                    .setParameter(7, remark)
                    .executeUpdate();
        } catch (Exception ignored) { }
    }
}
