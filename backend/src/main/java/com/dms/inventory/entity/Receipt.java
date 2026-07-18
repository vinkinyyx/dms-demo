/*
 * 收货单实体：映射 receipts 表。
 */
package com.dms.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "receipts")
@SQLRestriction("deleted_at IS NULL")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "receipt_type", length = 16)
    private String receiptType;

    @Column(name = "ref_doc_type", length = 16)
    private String refDocType;

    @Column(name = "ref_doc_id")
    private Long refDocId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(length = 16)
    private String status;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(columnDefinition = "text")
    private String remark;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
