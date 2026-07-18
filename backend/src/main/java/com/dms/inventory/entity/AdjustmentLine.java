/*
 * 库存调整单明细行：映射 adjustment_lines 表。
 */
package com.dms.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "adjustment_lines")
public class AdjustmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjustment_id")
    private Long adjustmentId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "serial_no", length = 64)
    private String serialNo;

    @Column(precision = 14, scale = 4)
    private BigDecimal qty;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
