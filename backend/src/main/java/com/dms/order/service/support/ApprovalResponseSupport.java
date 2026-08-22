package com.dms.order.service.support;

import com.dms.approval.entity.ApprovalInstance;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApprovalResponseSupport {
    private ApprovalResponseSupport() {}

    public static String resolveStatus(ApprovalInstance instance) {
        return instance.getStatus() == null ? "PENDING_APPROVAL" : instance.getStatus().name();
    }

    public static boolean isApproved(ApprovalInstance instance) {
        String status = resolveStatus(instance);
        return "APPROVED".equals(status) || "AUTO_APPROVED".equals(status);
    }

    public static Map<String, Object> submitResult(Long businessId, ApprovalInstance instance, boolean includeAutoApproved) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", businessId);
        result.put("newStatus", isApproved(instance) ? "APPROVED" : "PENDING_APPROVAL");
        result.put("approvalInstanceId", instance.getId());
        if (includeAutoApproved) {
            result.put("autoApproved", isApproved(instance));
        }
        return result;
    }

    public static Map<String, Object> decisionResult(Long businessId, ApprovalInstance instance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", businessId);
        result.put("newStatus", resolveStatus(instance));
        result.put("approvalInstanceId", instance.getId());
        return result;
    }
}