package com.dms.authz.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import com.dms.authz.entity.Authorization;
import com.dms.authz.repository.AuthorizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 授权审批回调：
 * - AUTHORIZATION：创建审批；AUTHORIZATION_RENEW：续约审批。通过 -> 按有效期置 active/not_started；驳回 -> rejected；撤回/退回 -> draft。
 * - AUTHORIZATION_TERMINATE：终止审批。通过 -> terminated；驳回/撤回/退回 -> 恢复原状态（active/not_started）。
 * 状态统一由本回调落库，Service/Controller 不再重复写状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationApprovalCallback implements ApprovalBusinessCallback {

    public static final String BT_AUTHORIZATION = "AUTHORIZATION";
    public static final String BT_AUTHORIZATION_TERMINATE = "AUTHORIZATION_TERMINATE";
    public static final String BT_AUTHORIZATION_RENEW = "AUTHORIZATION_RENEW";

    private final AuthorizationRepository authorizationRepository;

    @Override
    public boolean supports(String businessType) {
        return BT_AUTHORIZATION.equals(businessType) || BT_AUTHORIZATION_TERMINATE.equals(businessType)
                || BT_AUTHORIZATION_RENEW.equals(businessType);
    }

    @Override
    @Transactional
    public void onApproved(ApprovalInstance instance) {
        Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
        if (auth == null) return;
        if (BT_AUTHORIZATION_TERMINATE.equals(instance.getBusinessType())) {
            auth.setStatus("terminated");
            log.info("授权 {} 终止审批通过，状态=terminated", auth.getId());
        } else {
            LocalDate today = LocalDate.now();
            boolean notStarted = auth.getValidFrom() != null && auth.getValidFrom().isAfter(today);
            auth.setStatus(notStarted ? "not_started" : "active");
            log.info("授权 {} 审批通过（{}），状态={}", auth.getId(), instance.getBusinessType(), auth.getStatus());
        }
        auth.setUpdatedAt(OffsetDateTime.now());
        authorizationRepository.save(auth);
    }

    @Override
    @Transactional
    public void onRejected(ApprovalInstance instance) {
        Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
        if (auth == null) return;
        if (BT_AUTHORIZATION_TERMINATE.equals(instance.getBusinessType())) {
            auth.setStatus(prevStatus(instance, "active"));
        } else {
            auth.setStatus("rejected");
        }
        auth.setUpdatedAt(OffsetDateTime.now());
        authorizationRepository.save(auth);
    }

    @Override
    @Transactional
    public void onReturned(ApprovalInstance instance) { toDraftOrRestore(instance); }

    @Override
    @Transactional
    public void onWithdrawn(ApprovalInstance instance) { toDraftOrRestore(instance); }

    @Override
    @Transactional
    public void onTerminated(ApprovalInstance instance, String result) {
        if ("DRAFT".equalsIgnoreCase(result)) { toDraftOrRestore(instance); return; }
        Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
        if (auth == null) return;
        if (BT_AUTHORIZATION_TERMINATE.equals(instance.getBusinessType())) {
            auth.setStatus("terminated");
        } else {
            auth.setStatus("terminated");
        }
        auth.setUpdatedAt(OffsetDateTime.now());
        authorizationRepository.save(auth);
    }

    private void toDraftOrRestore(ApprovalInstance instance) {
        Authorization auth = authorizationRepository.findById(instance.getBusinessId()).orElse(null);
        if (auth == null) return;
        if (BT_AUTHORIZATION_TERMINATE.equals(instance.getBusinessType())) {
            auth.setStatus(prevStatus(instance, "active"));
        } else {
            auth.setStatus("draft");
        }
        auth.setUpdatedAt(OffsetDateTime.now());
        authorizationRepository.save(auth);
    }

    private String prevStatus(ApprovalInstance instance, String def) {
        try {
            Object prev = instance.getBusinessSnapshot() == null ? null
                    : instance.getBusinessSnapshot().get("prevStatus");
            if (prev != null && !String.valueOf(prev).isBlank()) return String.valueOf(prev);
        } catch (Exception ignored) {}
        return def;
    }
}