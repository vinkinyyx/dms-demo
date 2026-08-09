package com.dms.approval.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_instances")
public class ApprovalInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "template_id")
    private Long templateId;
    @Column(name = "template_version_no")
    private Integer templateVersionNo;
    @Column(name = "business_type", nullable = false, length = 64)
    private String businessType;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "business_code", length = 64)
    private String businessCode;
    @Column(nullable = false, length = 300)
    private String title;
    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;
    @Column(name = "submitter_name", length = 64)
    private String submitterName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalInstanceStatus status;
    @Column(name = "current_node_id")
    private Long currentNodeId;
    @Column(name = "current_node_name", length = 200)
    private String currentNodeName;
    @Enumerated(EnumType.STRING)
    @Column(name = "reject_policy", nullable = false, length = 32)
    private ApprovalRejectPolicy rejectPolicy;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "template_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> templateSnapshot;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "business_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> businessSnapshot;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;
}
