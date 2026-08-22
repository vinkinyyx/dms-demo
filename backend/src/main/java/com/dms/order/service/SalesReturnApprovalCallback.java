package com.dms.order.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.v4.V4OrderService;
import jakarta.persistence.EntityManager;
import com.dms.common.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesReturnApprovalCallback implements ApprovalBusinessCallback {
    public static final String BUSINESS_TYPE = "SALES_RETURN";
    private final EntityManager em;
    private final V4OrderService v4OrderService;

    @Override public boolean supports(String businessType) { return BUSINESS_TYPE.equals(businessType); }
    @Override public void onApproved(ApprovalInstance instance) {
        v4OrderService.approvePushErp(instance.getBusinessId(), true);
        em.createNativeQuery("UPDATE sales_out_lines sol SET returned_qty = returned_qty + ol.qty, return_locked_qty = GREATEST(return_locked_qty - ol.qty, 0) FROM order_lines ol WHERE ol.order_id = ?1 AND ol.extra IS NOT NULL AND sol.id = CAST(COALESCE(ol.extra->>'sourceOutLineId','0') AS bigint)")
          .setParameter(1, instance.getBusinessId()).executeUpdate();
        writeOpLog(instance, "APPROVE", "销退订单-审批通过");
    }
    @Override public void onReturned(ApprovalInstance instance) { releaseLocks(instance); setDraft(instance); writeOpLog(instance, "UPDATE", "销退订单-审批退回"); }
    @Override public void onRejected(ApprovalInstance instance) { releaseLocks(instance); em.createNativeQuery("UPDATE orders SET status='REJECTED', updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true").setParameter(1,instance.getBusinessId()).setParameter(2,instance.getTenantId()).executeUpdate(); writeOpLog(instance, "REJECT", "销退订单-审批驳回"); }
    @Override public void onWithdrawn(ApprovalInstance instance) { releaseLocks(instance); setDraft(instance); writeOpLog(instance, "UPDATE", "销退订单-撤回审批"); }
    @Override public void onTerminated(ApprovalInstance instance, String result) { if ("DRAFT".equalsIgnoreCase(result)) setDraft(instance); else em.createNativeQuery("UPDATE orders SET status='CANCELLED', closed_at=now(), cancelled_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true").setParameter(1,instance.getBusinessId()).setParameter(2,instance.getTenantId()).executeUpdate(); }
    private void setDraft(ApprovalInstance instance) { em.createNativeQuery("UPDATE orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true").setParameter(1,instance.getBusinessId()).setParameter(2,instance.getTenantId()).executeUpdate(); }
    private void releaseLocks(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE sales_out_lines sol SET return_locked_qty = GREATEST(return_locked_qty - ol.qty, 0) FROM order_lines ol WHERE ol.order_id = ?1 AND ol.extra IS NOT NULL AND sol.id = CAST(COALESCE(ol.extra->>'sourceOutLineId','0') AS bigint)")
          .setParameter(1, instance.getBusinessId()).executeUpdate();
    }
    private void writeOpLog(ApprovalInstance instance, String action, String remark) {
        try {
            em.createNativeQuery("INSERT INTO operation_log (tenant_code,business_type,business_id,operator_id,operator_name,action,remark,created_at,updated_at) VALUES (?1,?2,?3,?4,?5,?6,?7,now(),now())")
              .setParameter(1, instance.getTenantId() == null ? "default" : instance.getTenantId().toString())
              .setParameter(2, "salesReturn")
              .setParameter(3, instance.getBusinessId())
              .setParameter(4, TenantContext.getUserId())
              .setParameter(5, TenantContext.getUsername())
              .setParameter(6, action)
              .setParameter(7, remark)
              .executeUpdate();
        } catch (Exception ignored) { }
    }
}
