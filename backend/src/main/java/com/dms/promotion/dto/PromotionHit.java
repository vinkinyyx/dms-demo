/*
 * 促销命中记录 - 单条促销的命中详情。
 */
package com.dms.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromotionHit {

    private Long promotionId;
    private String promoType;
    private BigDecimal discount;
    private String detail;
}
