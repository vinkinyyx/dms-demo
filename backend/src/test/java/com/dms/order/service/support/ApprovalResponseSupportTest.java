package com.dms.order.service.support;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.entity.ApprovalInstanceStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalResponseSupportTest {
    @Test
    void submitResult_mapsRunningToPendingApproval() {
        ApprovalInstance instance = ApprovalInstance.builder().id(11L).status(ApprovalInstanceStatus.RUNNING).build();
        Map<String, Object> result = ApprovalResponseSupport.submitResult(9L, instance, true);
        assertThat(result).containsEntry("id", 9L);
        assertThat(result).containsEntry("newStatus", "PENDING_APPROVAL");
        assertThat(result).containsEntry("approvalInstanceId", 11L);
        assertThat(result).containsEntry("autoApproved", false);
    }

    @Test
    void submitResult_mapsApprovedToApprovedAndAutoApprovedTrue() {
        ApprovalInstance instance = ApprovalInstance.builder().id(12L).status(ApprovalInstanceStatus.AUTO_APPROVED).build();
        Map<String, Object> result = ApprovalResponseSupport.submitResult(9L, instance, true);
        assertThat(result).containsEntry("newStatus", "APPROVED");
        assertThat(result).containsEntry("autoApproved", true);
    }

    @Test
    void decisionResult_keepsRejectedStatusName() {
        ApprovalInstance instance = ApprovalInstance.builder().id(13L).status(ApprovalInstanceStatus.REJECTED).build();
        Map<String, Object> result = ApprovalResponseSupport.decisionResult(9L, instance);
        assertThat(result).containsEntry("newStatus", "REJECTED");
        assertThat(result).containsEntry("approvalInstanceId", 13L);
        assertThat(result).doesNotContainKey("autoApproved");
    }
}