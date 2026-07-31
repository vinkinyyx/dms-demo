/*
 * 在库序列号清单实体：映射 stock_serials 表（v3.7.3 V31）。
 * 用于支持医疗器械类商品按批次/序列号在库选择。
 */
package com.dms.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_serials")
public class StockSerial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "serial_no", nullable = false, length = 64)
    private String serialNo;

    @Column(name = "stock_status", nullable = false, length = 16)
    private String stockStatus;

    @Column(name = "source_doc_type", length = 16)
    private String sourceDocType;

    @Column(name = "source_doc_id")
    private Long sourceDocId;

    @Column(name = "source_line_id")
    private Long sourceLineId;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;
}