/*
 * 订单实体：映射 orders 表。
 */
package com.dms.order.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;

    @Column(name = "dealer_id", nullable = false)
    private Long dealerId;

    @Column(name = "ship_address_id")
    private Long shipAddressId;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "ship_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> shipSnapshot;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "parent_order_id")
    private Long parentOrderId;

    @Column(name = "amount_incl_tax", precision = 18, scale = 2)
    private BigDecimal amountInclTax;

    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", precision = 18, scale = 2)
    private BigDecimal finalAmount;

    @Column(columnDefinition = "text")
    private String remark;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_by")
    private Long createdBy;

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

    public void ensureSnapshot() {
        if (shipSnapshot == null) shipSnapshot = new HashMap<>();
    }
}
