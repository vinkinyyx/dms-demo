package com.dms.report.subscription;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "report_subscription")
public class ReportSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(name = "report_type", nullable = false, length = 64)
    private String reportType;
    @Column(columnDefinition = "text")
    private String params;
    @Column(name = "cron_expr", nullable = false, length = 64)
    private String cronExpr;
    @Column(columnDefinition = "text")
    private String emails;
    private Boolean active = true;
    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;
    @Column(name = "last_status", length = 16)
    private String lastStatus;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}