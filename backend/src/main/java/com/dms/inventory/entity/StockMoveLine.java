/*
 * 移库明细行实体：映射 stock_move_lines 表。
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
@Table(name = "stock_move_lines")
public class StockMoveLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "move_id")
    private Long moveId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "serial_no", length = 64)
    private String serialNo;

    @Column(precision = 14, scale = 4)
    private BigDecimal qty;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
