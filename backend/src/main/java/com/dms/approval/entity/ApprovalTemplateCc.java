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
@Table(name = "approval_template_ccs")
public class ApprovalTemplateCc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "template_id", nullable = false)
    private Long templateId;
    @Column(name = "node_id")
    private Long nodeId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "cc_type", nullable = false, length = 16)
    private ApprovalAssigneeType ccType;
    @Column(name = "ref_id", nullable = false)
    private Long refId;
    @Column(name = "display_name", length = 200)
    private String displayName;
    @Column(name = "cc_stage", nullable = false, length = 32)
    private String ccStage;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
