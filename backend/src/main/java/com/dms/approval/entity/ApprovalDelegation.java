package com.dms.approval.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_delegations")
@SQLRestriction("deleted_at IS NULL")
public class ApprovalDelegation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "delegator_id", nullable = false)
    private Long delegatorId;
    @Column(name = "delegatee_id", nullable = false)
    private Long delegateeId;
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;
    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(length = 500)
    private String reason;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
