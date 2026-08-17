package com.dms.asynctask.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "async_task")
public class AsyncTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType;

    @Column(name = "biz_type", length = 64)
    private String bizType;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "total_rows")
    private Integer totalRows = 0;

    @Column(name = "success_rows")
    private Integer successRows = 0;

    @Column(name = "failed_rows")
    private Integer failedRows = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(columnDefinition = "text")
    private String params;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_name", length = 64)
    private String createdName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}