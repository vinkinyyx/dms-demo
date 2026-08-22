package com.dms.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_bundle_lines")
@SQLRestriction("deleted_at IS NULL")
public class ProductBundleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(name = "child_product_id", nullable = false)
    private Long childProductId;

    @Column(name = "line_type", nullable = false, length = 16)
    private String lineType;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 16)
    private String status;

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

    @Transient
    private String childProductCode;

    @Transient
    private String childProductName;

    @Transient
    private String childProductSpec;
}
