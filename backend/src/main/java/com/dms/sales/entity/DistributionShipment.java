/*
 * 分销出库单实体：映射 distribution_shipments 表。
 */
package com.dms.sales.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "distribution_shipments")
@SQLRestriction("deleted_at IS NULL")
public class DistributionShipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 64)
    private String code;

    @Column(name = "from_dealer_id")
    private Long fromDealerId;

    @Column(name = "to_dealer_id")
    private Long toDealerId;

    @Column(name = "ref_order_id")
    private Long refOrderId;

    @Column(length = 16)
    private String status;

    @Column(name = "express_no", length = 64)
    private String expressNo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
