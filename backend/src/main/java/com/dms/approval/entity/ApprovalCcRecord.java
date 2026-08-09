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
@Table(name = "approval_cc_records")
public class ApprovalCcRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "instance_id", nullable = false)
    private Long instanceId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "user_name", length = 64)
    private String userName;
    @Column(nullable = false, length = 32)
    private String stage;
    @Column(name = "node_id")
    private Long nodeId;
    @Column(name = "read_at")
    private OffsetDateTime readAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
