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
@Table(name = "product_bundles")
@SQLRestriction("deleted_at IS NULL")
public class ProductBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "pricing_type", nullable = false, length = 16)
    private String pricingType;

    @Column(name = "bundle_price", precision = 14, scale = 2)
    private BigDecimal bundlePrice;

    @Column(name = "allow_split", nullable = false)
    private Boolean allowSplit;

    @Column(name = "split_rule", columnDefinition = "TEXT")
    private String splitRule;

    @Column(name = "version_note", columnDefinition = "TEXT")
    private String versionNote;

    @Column(name = "bom_version", nullable = false, length = 32)
    private String bomVersion;

    @Column(name = "version_status", nullable = false, length = 16)
    private String versionStatus;

    @Column(name = "version_locked", nullable = false)
    private Boolean versionLocked;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

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
    private java.util.List<ProductBundleLine> lines;

    @Transient
    private String productCode;

    @Transient
    private String productName;
}
