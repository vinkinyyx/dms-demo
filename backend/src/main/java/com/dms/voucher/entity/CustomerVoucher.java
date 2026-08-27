/*
 * 客户代金券实体：映射 customer_vouchers，厂家统一发放，一单一张，抵扣不摊入单价。
 */
package com.dms.voucher.entity;

import com.dms.common.jpa.JsonListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customer_vouchers")
@SQLRestriction("deleted_at IS NULL")
public class CustomerVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "face_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal faceValue;

    @Column(name = "min_spend", precision = 18, scale = 2)
    private BigDecimal minSpend;

    /** ALL / PRODUCT / CATEGORY */
    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "scope_refs", columnDefinition = "jsonb")
    private List<Map<String, Object>> scopeRefs;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    /** ISSUED / USED / EXPIRED / DISABLED / VOID / REVERSED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(length = 500)
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

    public void ensureScopeRefs() {
        if (scopeRefs == null) scopeRefs = new ArrayList<>();
    }
}
