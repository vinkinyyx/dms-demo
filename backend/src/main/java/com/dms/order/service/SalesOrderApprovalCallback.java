package com.dms.order.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.v4.V4OrderService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderApprovalCallback implements ApprovalBusinessCallback {
    public static final String BUSINESS_TYPE = "SALES_ORDER";
    private final EntityManager em;
    private final V4OrderService v4OrderService;

    @Override public boolean supports(String businessType) { return BUSINESS_TYPE.equals(businessType); }

    @Override
    public void onApproved(ApprovalInstance instance) {
        v4OrderService.approvePushErp(instance.getBusinessId(), false);
    }
    @Override public void onReturned(ApprovalInstance instance) { setDraft(instance); }
    @Override public void onRejected(ApprovalInstance instance) { em.createNativeQuery("UPDATE orders SET status='REJECTED', updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1,instance.getBusinessId()).setParameter(2,instance.getTenantId()).executeUpdate(); }
    @Override public void onWithdrawn(ApprovalInstance instance) { setDraft(instance); }
    @Override public void onTerminated(ApprovalInstance instance, String result) { if ("DRAFT".equalsIgnoreCase(result)) setDraft(instance); else em.createNativeQuery("UPDATE orders SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1,instance.getBusinessId()).setParameter(2,instance.getTenantId()).executeUpdate(); }
    private void setDraft(ApprovalInstance instance) { em.createNativeQuery("UPDATE orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1,instance.getBusinessId()).setParameter(2,instance.getTenantId()).executeUpdate(); }
}