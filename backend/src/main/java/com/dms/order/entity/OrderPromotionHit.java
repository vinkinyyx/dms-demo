/*
 * 订单命中的促销记录实体：映射 order_promotion_hits 表。
 */
package com.dms.order.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_promotion_hits")
public class OrderPromotionHit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Column(name = "rule_type", length = 16)
    private String ruleType;

    @Column(precision = 18, scale = 2)
    private BigDecimal discount;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "gift_lines", columnDefinition = "jsonb")
    private Map<String, Object> giftLines;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "detail", columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
