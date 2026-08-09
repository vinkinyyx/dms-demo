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
@Table(name = "approval_template_nodes")
public class ApprovalTemplateNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "template_id", nullable = false)
    private Long templateId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "node_order", nullable = false)
    private Integer nodeOrder;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "approve_mode", nullable = false, length = 16)
    private ApprovalApproveMode approveMode;
    @Column(name = "allow_transfer", nullable = false)
    private Boolean allowTransfer;
    @Column(name = "allow_add_sign", nullable = false)
    private Boolean allowAddSign;
    @Column(name = "timeout_hours")
    private Integer timeoutHours;
    @Column(name = "remind_interval_hours")
    private Integer remindIntervalHours;
    @Column(name = "max_remind_count")
    private Integer maxRemindCount;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
