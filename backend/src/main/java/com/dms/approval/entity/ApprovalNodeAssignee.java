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
@Table(name = "approval_node_assignees")
public class ApprovalNodeAssignee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "node_id", nullable = false)
    private Long nodeId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type", nullable = false, length = 16)
    private ApprovalAssigneeType assigneeType;
    @Column(name = "ref_id", nullable = false)
    private Long refId;
    @Column(name = "display_name", length = 200)
    private String displayName;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
