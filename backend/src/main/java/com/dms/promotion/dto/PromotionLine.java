/*
 * 促销引擎入参 - 单行商品。
 */
package com.dms.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PromotionLine {

    private Long productId;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal subTotal;
}
