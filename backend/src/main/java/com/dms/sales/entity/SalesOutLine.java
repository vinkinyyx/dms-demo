/*
 * 销售出库明细行：映射 sales_out_lines 表。
 */
package com.dms.sales.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_out_lines")
public class SalesOutLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_out_id")
    private Long salesOutId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "serial_no", length = 64)
    private String serialNo;

    @Column(name = "stock_batch_id")
    private Long stockBatchId;

    @Column(name = "expected_qty", precision = 14, scale = 4)
    private BigDecimal expectedQty;

    @Column(name = "shipped_qty", precision = 14, scale = 4)
    private BigDecimal shippedQty;

    @Column(name = "seq")
    private Integer seq;

    @Column(name = "unit_price", precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "subtotal", precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 14, scale = 4)
    private BigDecimal qty;

    @Column(name = "cancelled_qty", precision = 14, scale = 4)
    private BigDecimal cancelledQty;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

