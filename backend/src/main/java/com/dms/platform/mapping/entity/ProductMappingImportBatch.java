/*
 * 产品对码导入批次，映射 product_mapping_import_batches。
 */
package com.dms.platform.mapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_mapping_import_batches")
public class ProductMappingImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manufacturer_tenant_id", nullable = false)
    private UUID manufacturerTenantId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "error_object_key")
    private String errorObjectKey;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}