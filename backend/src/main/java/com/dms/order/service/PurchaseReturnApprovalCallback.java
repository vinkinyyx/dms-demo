package com.dms.order.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.execution.service.AutoDocGenerator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseReturnApprovalCallback implements ApprovalBusinessCallback {
    public static final String BUSINESS_TYPE = "PURCHASE_RETURN";
    private final EntityManager em;
    private final AutoDocGenerator autoDocGenerator;

    @Override
    public boolean supports(String businessType) {
        return BUSINESS_TYPE.equals(businessType);
    }

    @Override
    public void onApproved(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE purchase_orders SET status='APPROVED', approved_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true")
                .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        try {
            Long soId = autoDocGenerator.createSalesOutForPurchaseReturn(instance.getBusinessId());
            log.info("purchase return {} approved, auto sales out {}", instance.getBusinessId(), soId);
        } catch (Exception e) {
            log.warn("purchase return {} create sales out failed: {}", instance.getBusinessId(), e.getMessage());
        }
    }

    @Override
    public void onReturned(ApprovalInstance instance) { setDraft(instance); }

    @Override
    public void onRejected(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE purchase_orders SET status='REJECTED', updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true")
                .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
    }

    @Override
    public void onWithdrawn(ApprovalInstance instance) { setDraft(instance); }

    @Override
    public void onTerminated(ApprovalInstance instance, String result) {
        if ("DRAFT".equalsIgnoreCase(result)) setDraft(instance);
        else em.createNativeQuery("UPDATE purchase_orders SET status='CANCELLED', completed_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true")
                .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
    }

    private void setDraft(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE purchase_orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true")
                .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
    }
}