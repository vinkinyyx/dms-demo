package com.dms.masterdata.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierCreateApprovalCallback implements ApprovalBusinessCallback {
    public static final String BUSINESS_TYPE = "SUPPLIER_CREATE";

    private final EntityManager em;

    @Override public boolean supports(String businessType) { return BUSINESS_TYPE.equals(businessType); }

    @Override public void onApproved(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE suppliers SET status='active', updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        writeOpLog(instance, "APPROVE", "供应商创建-审批通过");
    }
    @Override public void onRejected(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE suppliers SET status='draft', updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        writeOpLog(instance, "REJECT", "供应商创建-审批驳回");
    }
    @Override public void onReturned(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE suppliers SET status='draft', updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        writeOpLog(instance, "UPDATE", "供应商创建-审批退回");
    }
    @Override public void onWithdrawn(ApprovalInstance instance) {
        em.createNativeQuery("UPDATE suppliers SET status='draft', updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        writeOpLog(instance, "UPDATE", "供应商创建-撤回审批");
    }
    @Override public void onTerminated(ApprovalInstance instance, String result) {
        em.createNativeQuery("UPDATE suppliers SET status='draft', updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, instance.getBusinessId()).setParameter(2, instance.getTenantId()).executeUpdate();
        writeOpLog(instance, "UPDATE", "供应商创建-审批终止");
    }

    private void writeOpLog(ApprovalInstance instance, String action, String remark) {
        try {
            em.createNativeQuery("INSERT INTO operation_log (tenant_code,business_type,business_id,operator_id,operator_name,action,remark,created_at,updated_at) VALUES (?1,?2,?3,?4,?5,?6,?7,now(),now())")
              .setParameter(1, instance.getTenantId() == null ? "default" : instance.getTenantId().toString())
              .setParameter(2, "supplier")
              .setParameter(3, instance.getBusinessId())
              .setParameter(4, TenantContext.getUserId())
              .setParameter(5, TenantContext.getUsername())
              .setParameter(6, action)
              .setParameter(7, remark)
              .executeUpdate();
        } catch (Exception ignored) { }
    }
}
