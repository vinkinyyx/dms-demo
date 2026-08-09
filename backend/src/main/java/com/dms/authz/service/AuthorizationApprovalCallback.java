package com.dms.authz.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.authz.entity.Authorization;
import com.dms.authz.repository.AuthorizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationApprovalCallback implements ApprovalBusinessCallback {
    public static final String BUSINESS_TYPE = "AUTHORIZATION";
    private final AuthorizationRepository authorizationRepository;

    @Override
    public boolean supports(String businessType) {
        return BUSINESS_TYPE.equals(businessType);
    }

    @Override
    public void onApproved(ApprovalInstance instance) {
        Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
        if (auth == null) return;
        auth.setStatus("active");
        auth.setUpdatedAt(OffsetDateTime.now());
        authorizationRepository.save(auth);
    }

    @Override
    public void onReturned(ApprovalInstance instance) { toDraft(instance); }

    @Override
    public void onRejected(ApprovalInstance instance) {
        Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
        if (auth == null) return;
        auth.setStatus("rejected");
        auth.setUpdatedAt(OffsetDateTime.now());
        authorizationRepository.save(auth);
    }

    @Override
    public void onWithdrawn(ApprovalInstance instance) { toDraft(instance); }

    @Override
    public void onTerminated(ApprovalInstance instance, String result) {
        if ("DRAFT".equalsIgnoreCase(result)) toDraft(instance);
        else {
            Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
            if (auth == null) return;
            auth.setStatus("terminated");
            auth.setUpdatedAt(OffsetDateTime.now());
            authorizationRepository.save(auth);
        }
    }

    private void toDraft(ApprovalInstance instance) {
        Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
        if (auth == null) return;
        auth.setStatus("draft");
        auth.setUpdatedAt(OffsetDateTime.now());
        authorizationRepository.save(auth);
    }
}