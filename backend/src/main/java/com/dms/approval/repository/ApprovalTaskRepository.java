/*
 * 审批任务仓储接口。
 */
package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalTask;
import com.dms.approval.entity.ApprovalTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {
    Page<ApprovalTask> findByTenantIdAndAssigneeIdAndStatusOrderByCreatedAtDesc(UUID tenantId, Long assigneeId, ApprovalTaskStatus status, Pageable pageable);
    Page<ApprovalTask> findByTenantIdAndAssigneeIdAndStatusNotOrderByHandledAtDesc(UUID tenantId, Long assigneeId, ApprovalTaskStatus status, Pageable pageable);
    List<ApprovalTask> findByInstanceIdAndStatusOrderByIdAsc(Long instanceId, ApprovalTaskStatus status);
    List<ApprovalTask> findByInstanceIdOrderByIdAsc(Long instanceId);
    List<ApprovalTask> findByStatusAndDueAtBefore(ApprovalTaskStatus status, OffsetDateTime dueAt);

    java.util.Optional<ApprovalTask> findFirstByInstanceIdAndAssigneeIdAndStatusOrderByIdAsc(Long instanceId, Long assigneeId, ApprovalTaskStatus status);
}