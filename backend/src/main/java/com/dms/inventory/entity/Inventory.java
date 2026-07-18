/*
 * 库存实体：映射 inventory 表。
 */
package com.dms.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "serial_no", length = 64)
    private String serialNo;

    @Column(name = "prod_date")
    private LocalDate prodDate;

    @Column(name = "exp_date")
    private LocalDate expDate;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qty;

    @Column(name = "in_source", length = 32)
    private String inSource;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;
}
