package com.dms.contract.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.common.util.TenantContext;
import com.dms.contract.entity.Contract;
import com.dms.contract.entity.ContractRevision;
import com.dms.contract.repository.ContractRepository;
import com.dms.contract.repository.ContractRevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 合同审批回调：
 * - CONTRACT：创建/提交审批。通过 -> effective（TERMINATE 申请类型为 terminated）；驳回 -> rejected；撤回/退回 -> draft。
 * - CONTRACT_TERMINATE：已生效合同的终止审批。通过 -> terminated；驳回/撤回/退回 -> 恢复 effective。
 * 合同状态统一由本回调落库，Controller/Service 不再重复写状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractApprovalCallback implements ApprovalBusinessCallback {

    public static final String BUSINESS_TYPE = "CONTRACT";
    public static final String TERMINATE_TYPE = "CONTRACT_TERMINATE";

    private final ContractRepository contractRepository;
    private final ContractRevisionRepository revisionRepository;

    @Override
    public boolean supports(String businessType) {
        return BUSINESS_TYPE.equals(businessType) || TERMINATE_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void onApproved(ApprovalInstance instance) {
        Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
        if (c == null) return;
        if (TERMINATE_TYPE.equals(instance.getBusinessType())) {
            apply(c, "terminated", "terminate_approve", null);
            log.info("合同 {} 终止审批通过，状态=terminated", c.getCode());
        } else {
            String target = "TERMINATE".equals(c.getApplicationType()) ? "terminated" : "effective";
            apply(c, target, "approve", null);
            log.info("合同 {} 审批通过，状态={}", c.getCode(), target);
        }
    }

    @Override
    @Transactional
    public void onReturned(ApprovalInstance instance) { toDraftOrRestore(instance); }

    @Override
    @Transactional
    public void onRejected(ApprovalInstance instance) {
        Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
        if (c == null) return;
        if (TERMINATE_TYPE.equals(instance.getBusinessType())) {
            apply(c, "effective", "terminate_reject", null);
        } else {
            apply(c, "rejected", "reject", null);
        }
    }

    @Override
    @Transactional
    public void onWithdrawn(ApprovalInstance instance) { toDraftOrRestore(instance); }

    @Override
    @Transactional
    public void onTerminated(ApprovalInstance instance, String result) {
        if ("DRAFT".equalsIgnoreCase(result)) { toDraftOrRestore(instance); return; }
        Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
        if (c == null) return;
        if (TERMINATE_TYPE.equals(instance.getBusinessType())) {
            apply(c, "terminated", "terminate_approve", null);
        } else {
            apply(c, "terminated", "terminate", null);
        }
    }

    private void toDraftOrRestore(ApprovalInstance instance) {
        Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
        if (c == null) return;
        if (TERMINATE_TYPE.equals(instance.getBusinessType())) {
            apply(c, "effective", "terminate_cancel", null);
        } else {
            apply(c, "draft", "return", null);
        }
    }

    private void apply(Contract c, String status, String action, String comment) {
        c.setStatus(status);
        c.setUpdatedAt(OffsetDateTime.now());
        if ("effective".equals(status)) c.setEffectiveAt(OffsetDateTime.now());
        if ("terminated".equals(status)) c.setTerminatedAt(OffsetDateTime.now());
        contractRepository.save(c);
        revisionRepository.save(ContractRevision.builder()
                .tenantId(c.getTenantId())
                .contractId(c.getId())
                .round((int) revisionRepository.countByContractId(c.getId()) + 1)
                .action(action)
                .operatorId(TenantContext.getUserId())
                .operatorName(TenantContext.getUsername())
                .comment(comment)
                .build());
    }
}