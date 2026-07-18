/*
 * 商品主数据实体：映射 products 表，包含税率、UDI、安全库存等字段。
 */
package com.dms.masterdata.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "name_cn", nullable = false, length = 200)
    private String nameCn;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(length = 100)
    private String spec;

    @Column(length = 32)
    private String unit;

    @Column(name = "current_price", precision = 14, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "udi_required")
    private Boolean udiRequired;

    @Column(name = "warn_months")
    private Integer warnMonths;

    @Column(name = "safety_qty", precision = 14, scale = 4)
    private BigDecimal safetyQty;

    @Column(name = "min_order_qty", precision = 14, scale = 4)
    private BigDecimal minOrderQty;

    @Column(length = 16)
    private String status;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "attrs", columnDefinition = "jsonb")
    private Map<String, Object> attrs;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureAttrs() {
        if (attrs == null) attrs = new HashMap<>();
    }
}
