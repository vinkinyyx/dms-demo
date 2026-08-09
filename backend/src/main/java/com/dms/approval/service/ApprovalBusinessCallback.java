package com.dms.approval.service;

import com.dms.approval.entity.ApprovalInstance;

public interface ApprovalBusinessCallback {
    boolean supports(String businessType);

    default void onApproved(ApprovalInstance instance) {
    }

    default void onReturned(ApprovalInstance instance) {
    }

    default void onRejected(ApprovalInstance instance) {
    }

    default void onWithdrawn(ApprovalInstance instance) {
    }

    default void onTerminated(ApprovalInstance instance, String result) {
    }
}