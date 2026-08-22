/*
 * SalesReturnLineSupport 纯逻辑单测：覆盖行解析、数量聚合、单价/金额计算。
 */
package com.dms.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SalesReturnLineSupportTest {

    private final SalesReturnLineSupport support = new SalesReturnLineSupport();

    private Map<String, Object> line(Object sourceOutLineId, Object qty) {
        return Map.of("sourceOutLineId", sourceOutLineId, "qty", qty);
    }

    @Test
    void aggregate_emptyLines_returnsError() {
        var result = support.aggregate(List.of());
        assertThat(result.hasError()).isTrue();
        assertThat(result.error()).isEqualTo("请添加销退明细");
    }

    @Test
    void aggregate_missingSourceOutLine_returnsRowError() {
        var result = support.aggregate(List.of(Map.of("qty", 1)));
        assertThat(result.error()).isEqualTo("第 1 行缺少原出库行");
    }

    @Test
    void aggregate_nonPositiveQty_returnsRowError() {
        var result = support.aggregate(List.of(line(10L, 0)));
        assertThat(result.error()).isEqualTo("第 1 行退货数量必须大于0");
    }

    @Test
    void aggregate_mergesSameSourceOutLineQuantities() {
        var result = support.aggregate(List.of(
                line(100L, 2),
                Map.of("id", 100L, "qty", 3),
                line(200L, "1.5")
        ));
        assertThat(result.hasError()).isFalse();
        assertThat(result.quantities()).containsEntry(100L, new BigDecimal("5"));
        assertThat(result.quantities()).containsEntry(200L, new BigDecimal("1.5"));
    }

    @Test
    void resolveUnitPrice_dividesFinalAmountByShippedQty() {
        assertThat(support.resolveUnitPrice(new BigDecimal("5"), new BigDecimal("100.00"), 999))
                .isEqualByComparingTo("20.0000");
    }

    @Test
    void resolveUnitPrice_fallsBackWhenShippedZero() {
        assertThat(support.resolveUnitPrice(BigDecimal.ZERO, BigDecimal.ZERO, "12.50"))
                .isEqualByComparingTo("12.50");
    }

    @Test
    void calcLineTotal_scalesViaV4Money() {
        assertThat(support.calcLineTotal(new BigDecimal("3"), new BigDecimal("33.3333")))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void firstLong_picksFirstPresentKey() {
        assertThat(support.firstLong(Map.of("sourceSalesOutId", "42"), "refSalesOutId", "sourceSalesOutId"))
                .isEqualTo(42L);
        assertThat(support.firstLong(Map.of(), "missing")).isNull();
    }

    @Test
    void firstString_trimsAndSkipsBlank() {
        assertThat(support.firstString(Map.of("reason", "  质量问题  "), "returnReason", "reason"))
                .isEqualTo("质量问题");
    }

    @Test
    void jsonLong_readsSourceOutLineIdFromJson() {
        assertThat(support.jsonLong("{\"sourceOutLineId\":77}", "sourceOutLineId")).isEqualTo(77L);
        assertThat(support.jsonLong("not-json", "sourceOutLineId")).isNull();
        assertThat(support.jsonLong(null, "sourceOutLineId")).isNull();
    }
}