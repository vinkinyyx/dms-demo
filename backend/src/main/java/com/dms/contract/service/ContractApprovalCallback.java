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

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractApprovalCallback implements ApprovalBusinessCallback {

    public static final String BUSINESS_TYPE = "CONTRACT";
    private final ContractRepository contractRepository;
    private final ContractRevisionRepository revisionRepository;

    @Override
    public boolean supports(String businessType) {
        return BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void onApproved(ApprovalInstance instance) {
        Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
        if (c == null) return;
        String target = "TERMINATE".equals(c.getApplicationType()) ? "terminated" : "effective";
        apply(c, target, null);
        log.info("合同 {} 审批通过，状态更新为 {}", c.getCode(), target);
    }

    @Override
    @Transactional
    public void onReturned(ApprovalInstance instance) {
        toDraft(instance);
    }

    @Override
    @Transactional
    public void onRejected(ApprovalInstance instance) {
        Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
        if (c == null) return;
        apply(c, "rejected", null);
    }

    @Override
    @Transactional
    public void onWithdrawn(ApprovalInstance instance) {
        toDraft(instance);
    }

    @Override
    @Transactional
    public void onTerminated(ApprovalInstance instance, String result) {
        if ("DRAFT".equalsIgnoreCase(result)) {
            toDraft(instance);
        } else {
            Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
            if (c == null) return;
            apply(c, "terminated", null);
        }
    }

    private void toDraft(ApprovalInstance instance) {
        Contract c = contractRepository.findById(instance.getBusinessId()).orElse(null);
        if (c == null) return;
        apply(c, "draft", null);
    }

    private void apply(Contract c, String status, String comment) {
        c.setStatus(status);
        c.setUpdatedAt(OffsetDateTime.now());
        if ("effective".equals(status)) c.setEffectiveAt(OffsetDateTime.now());
        if ("terminated".equals(status)) c.setTerminatedAt(OffsetDateTime.now());
        contractRepository.save(c);
        revisionRepository.save(ContractRevision.builder()
                .tenantId(c.getTenantId())
                .contractId(c.getId())
                .round((int) revisionRepository.countByContractId(c.getId()) + 1)
                .action("rejected".equals(status) || "draft".equals(status) ? "reject" : "approve")
                .operatorId(TenantContext.getUserId())
                .operatorName(TenantContext.getUsername())
                .comment(comment)
                .build());
    }
}