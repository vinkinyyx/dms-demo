/*
 * 盘点明细行实体：映射 stocktake_lines 表。
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
@Table(name = "stocktake_lines")
public class StocktakeLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stocktake_id")
    private Long stocktakeId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "serial_no", length = 64)
    private String serialNo;

    @Column(name = "book_qty", precision = 14, scale = 4)
    private BigDecimal bookQty;

    @Column(name = "actual_qty", precision = 14, scale = 4)
    private BigDecimal actualQty;

    @Column(name = "diff_qty", precision = 14, scale = 4)
    private BigDecimal diffQty;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
