package com.dms.approval.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_tasks")
public class ApprovalTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "instance_id", nullable = false)
    private Long instanceId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "node_id")
    private Long nodeId;
    @Column(name = "node_name", length = 200)
    private String nodeName;
    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;
    @Column(name = "assignee_name", length = 64)
    private String assigneeName;
    @Column(name = "original_assignee_id")
    private Long originalAssigneeId;
    @Column(name = "delegated_from_user_id")
    private Long delegatedFromUserId;
    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType;
    @Column(name = "parent_task_id")
    private Long parentTaskId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalTaskStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "approve_mode", nullable = false, length = 16)
    private ApprovalApproveMode approveMode;
    @Column(length = 1000)
    private String comment;
    @Column(name = "due_at")
    private OffsetDateTime dueAt;
    @Column(name = "reminded_count", nullable = false)
    private Integer remindedCount;
    @Column(name = "last_reminded_at")
    private OffsetDateTime lastRemindedAt;
    @Column(name = "handled_at")
    private OffsetDateTime handledAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;

    @Transient
    private String title;
    @Transient
    private String businessType;
    @Transient
    private String businessCode;
    @Transient
    private String submitterName;
    @Transient
    private String instanceStatus;
}
