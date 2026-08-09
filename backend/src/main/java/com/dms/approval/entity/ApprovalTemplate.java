package com.dms.approval.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_templates")
@SQLRestriction("deleted_at IS NULL")
public class ApprovalTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "business_type", nullable = false, length = 64)
    private String businessType;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;
    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 32)
    private ApprovalTemplateType templateType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalTemplateStatus status;
    @Column(nullable = false)
    private Integer priority;
    @Enumerated(EnumType.STRING)
    @Column(name = "reject_policy", nullable = false, length = 32)
    private ApprovalRejectPolicy rejectPolicy;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "condition_config", columnDefinition = "jsonb")
    private Map<String, Object> conditionConfig;
    @Column(name = "timeout_hours")
    private Integer timeoutHours;
    @Column(name = "remind_interval_hours")
    private Integer remindIntervalHours;
    @Column(name = "max_remind_count")
    private Integer maxRemindCount;
    @Column(length = 500)
    private String description;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
    @Column(name = "published_by")
    private Long publishedBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "updated_by")
    private Long updatedBy;
    @Version
    private Integer version;
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
