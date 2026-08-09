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
@Table(name = "approval_records")
public class ApprovalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "instance_id", nullable = false)
    private Long instanceId;
    @Column(name = "task_id")
    private Long taskId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 32)
    private String action;
    @Column(name = "node_id")
    private Long nodeId;
    @Column(name = "node_name", length = 200)
    private String nodeName;
    @Column(name = "operator_id")
    private Long operatorId;
    @Column(name = "operator_name", length = 64)
    private String operatorName;
    @Column(length = 1000)
    private String comment;
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
