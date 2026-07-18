/*
 * 促销活动实体：映射 promotions 表。promo_type V1 白名单 MOQ / FULL_REDUCTION。
 */
package com.dms.promotion.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promotions")
@SQLRestriction("deleted_at IS NULL")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "promo_type", nullable = false, length = 16)
    private String promoType;

    @Column
    private Integer priority;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "dealer_scope", columnDefinition = "jsonb")
    private Map<String, Object> dealerScope;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "product_scope", columnDefinition = "jsonb")
    private Map<String, Object> productScope;

    @Column
    private Boolean exclusive;

    @Column(length = 16)
    private String status;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureMaps() {
        if (dealerScope == null) dealerScope = new HashMap<>();
        if (productScope == null) productScope = new HashMap<>();
    }
}
