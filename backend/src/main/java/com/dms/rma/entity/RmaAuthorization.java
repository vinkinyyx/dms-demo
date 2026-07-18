/*
 * RMA 授权实体：映射 rma_authorizations 表。
 */
package com.dms.rma.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rma_authorizations")
@SQLRestriction("deleted_at IS NULL")
public class RmaAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 64)
    private String code;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "product_line_id")
    private Long productLineId;

    @Column(name = "quota_amount", precision = 18, scale = 2)
    private BigDecimal quotaAmount;

    @Column(name = "quota_used", precision = 18, scale = 2)
    private BigDecimal quotaUsed;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(length = 16)
    private String status;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
