/*
 * 库存流水实体：映射 inventory_transactions 分区表主表。
 * 分区表 DB PK 是 (id, at_time)，但 id 由 BIGSERIAL 生成且唯一，Java 侧只声明 id 为主键；
 * at_time 作为普通列写入，读取场景基本不通过 findById，避免 @IdClass 与生成主键冲突。
 */
package com.dms.inventory.entity;

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
@Table(name = "inventory_transactions")
public class InventoryTransaction {

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

    @Column(name = "qty_change", nullable = false, precision = 14, scale = 4)
    private BigDecimal qtyChange;

    @Column(name = "txn_type", nullable = false, length = 32)
    private String txnType;

    @Column(name = "ref_doc_type", length = 32)
    private String refDocType;

    @Column(name = "ref_doc_id")
    private Long refDocId;

    @Column(name = "at_time")
    private OffsetDateTime atTime;

    @Column(name = "operator_id")
    private Long operatorId;
}
