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

    @Column(precision = 14, scale = 4)
    private BigDecimal qty;

    /** 报价单价（用于金额计算，可选，实体表未存储）。 */
    @Transient
    private BigDecimal unitPrice;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
