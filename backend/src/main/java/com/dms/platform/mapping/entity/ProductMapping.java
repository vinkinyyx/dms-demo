/*
 * 产品对码实体，映射 product_mappings：厂家产品与经销商产品一对一编码映射。
 */
package com.dms.platform.mapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_mappings")
@SQLRestriction("deleted_at IS NULL")
public class ProductMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manufacturer_tenant_id", nullable = false)
    private UUID manufacturerTenantId;

    @Column(name = "dealer_tenant_id", nullable = false)
    private UUID dealerTenantId;

    @Column(name = "manufacturer_product_id", nullable = false)
    private Long manufacturerProductId;

    @Column(name = "dealer_product_id", nullable = false)
    private Long dealerProductId;

    @Column(name = "manufacturer_product_code", nullable = false, length = 64)
    private String manufacturerProductCode;

    @Column(name = "dealer_product_code", nullable = false, length = 64)
    private String dealerProductCode;

    @Column(name = "package_unit", length = 32)
    private String packageUnit;

    @Column(name = "conversion_rate", precision = 18, scale = 6, nullable = false)
    private BigDecimal conversionRate;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "import_batch_no", length = 64)
    private String importBatchNo;

    @Column(name = "remark", length = 500)
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
    @Column(name = "version")
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}