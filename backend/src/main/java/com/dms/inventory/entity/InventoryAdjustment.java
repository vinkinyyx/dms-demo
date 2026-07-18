/*
 * 库存调整单实体：映射 inventory_adjustments 表。
 */
package com.dms.inventory.entity;

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
@Table(name = "inventory_adjustments")
@SQLRestriction("deleted_at IS NULL")
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 64)
    private String code;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "adj_category", length = 16)
    private String adjCategory;

    @Column(name = "adj_type", length = 32)
    private String adjType;

    @Column(length = 16)
    private String status;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
