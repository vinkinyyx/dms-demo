/*
 * 授权实体：映射 authorizations 表，包含 ORDER/SALES_TO_HOSPITAL/RMA 等 auth_type。
 */
package com.dms.authz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "authorizations")
@SQLRestriction("deleted_at IS NULL")
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "auth_type", length = 32)
    private String authType;

    @Column(name = "product_line_id")
    private Long productLineId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "product_lines", length = 500)
    private String productLines;

    @Column(name = "category_ids", length = 500)
    private String categoryIds;

    @Column(name = "terminal_ids", length = 1000)
    private String terminalIds;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(length = 16)
    private String status;

    @Column(length = 16)
    private String source;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Transient
    private String dealerName;

    @Transient
    private String categoryNames;

    @Transient
    private String terminalNames;
}
