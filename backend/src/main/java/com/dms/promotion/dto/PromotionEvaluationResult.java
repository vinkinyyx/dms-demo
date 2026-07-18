/*
 * 促销引擎输出：hits[], gifts[], discountTotal, warnings[], rejected（BLOCK 是否拒绝）。
 */
package com.dms.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromotionEvaluationResult {

    private List<PromotionHit> hits;
    private List<PromotionLine> gifts;
    private BigDecimal discountTotal;
    private List<String> warnings;
    private Boolean rejected;
    private List<String> rejectedReasons;

    public static PromotionEvaluationResult empty() {
        return PromotionEvaluationResult.builder()
                .hits(new ArrayList<>())
                .gifts(new ArrayList<>())
                .discountTotal(BigDecimal.ZERO)
                .warnings(new ArrayList<>())
                .rejected(false)
                .rejectedReasons(new ArrayList<>())
                .build();
    }
}
