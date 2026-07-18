/*
 * 收货单明细实体：映射 receipt_lines 表。
 */
package com.dms.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "receipt_lines")
public class ReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id")
    private Long receiptId;

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

    @Column(name = "expected_qty", precision = 14, scale = 4)
    private BigDecimal expectedQty;

    @Column(name = "received_qty", precision = 14, scale = 4)
    private BigDecimal receivedQty;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
