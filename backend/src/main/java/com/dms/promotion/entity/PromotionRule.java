/*
 * 促销规则实体：映射 promotion_rules 表，rule_detail 用 JSONB 存储。
 * MOQ: {productId, minQty, mode: 'BLOCK'|'WARN'}
 * FULL_REDUCTION: {tiers:[{amount,reduce}], scope:'ORDER'|'PRODUCT', exclusive}
 */
package com.dms.promotion.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promotion_rules")
public class PromotionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column
    private Integer seq;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "rule_detail", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> ruleDetail;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public void ensureDetail() {
        if (ruleDetail == null) ruleDetail = new HashMap<>();
    }
}
