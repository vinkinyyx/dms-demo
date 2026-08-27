/*
 * RMA 订单关系化明细：映射 rma_order_lines 表。
 */
package com.dms.rma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rma_order_lines")
public class RmaOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "rma_id", nullable = false)
    private Long rmaId;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "sales_out_line_id")
    private Long salesOutLineId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "product_spec", length = 200)
    private String productSpec;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "unit_price_incl_tax", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPriceInclTax;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "sub_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal subTotal;

    @Column(length = 500)
    private String reason;

    @Column(name = "batch_no", length = 128)
    private String batchNo;

    @Column(name = "serial_no", length = 128)
    private String serialNo;

    private Integer seq;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
