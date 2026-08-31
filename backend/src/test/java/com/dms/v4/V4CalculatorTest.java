package com.dms.v4;

import jakarta.persistence.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V4CalculatorTest {
    @Mock
    V4PricingService pricing;
    @InjectMocks
    V4Calculator calculator;

    @Test
    void bomComponentUsesBomPriceAndChildDiscountHasHighestPriority() {
        UUID tid = UUID.randomUUID();
        Long dealerId = 1L;
        when(pricing.isBom(tid, 100L)).thenReturn(true);
        when(pricing.currentBomVersion(tid, 100L)).thenReturn("1.0");
        when(pricing.bomLines(tid, 100L, "1.0")).thenReturn(List.of(Map.of(
                "productId", 200L, "code", "CHILD", "name", "child", "spec", "S",
                "quantity", new BigDecimal("1"), "bomVersion", "1.0", "serialManaged", false)));
        Tuple product = tuple(mapOf("code", "BOM", "name_cn", "parent", "spec", "B", "tax_rate", new BigDecimal("0.13"), "product_line_id", null));
        when(pricing.product(eq(tid), eq(100L))).thenReturn(product);
        when(pricing.salesPrice(eq(tid), eq(200L), eq(dealerId), eq(V4PricingService.PriceUse.BOM_COMPONENT), eq(100L)))
                .thenReturn(new V4PricingService.Price(new BigDecimal("884.9558"), new BigDecimal("0.13"), new BigDecimal("1000")));

        V4CalcResult result = calculator.expand(tid, dealerId, List.of(Map.of(
                "productId", 100L,
                "qty", new BigDecimal("1"),
                "bomVersion", "1.0",
                "childDiscounts", List.of(Map.of("productId", 200L, "lineDiscountType", "AMOUNT", "lineDiscountValue", new BigDecimal("100")))
        )), false, "AMOUNT", new BigDecimal("100"));

        V4Line child = result.getLines().stream().filter(l -> "CHILD".equals(l.getLineLevel())).findFirst().orElseThrow();
        V4Line parent = result.getLines().stream().filter(l -> "PARENT".equals(l.getLineLevel())).findFirst().orElseThrow();
        assertThat(parent.getFinalAmount()).isEqualByComparingTo("0");
        assertThat(child.getLineDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(child.getHeaderDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(child.getFinalAmount()).isEqualByComparingTo("800.00");
        assertThat(child.getStandardPriceInclTax()).isEqualByComparingTo("1000");
    }

    @Test
    void standaloneLinesAllocateAfterLineDiscountByAfterLineDiscountBase() {
        UUID tid = UUID.randomUUID();
        Long dealerId = 1L;
        when(pricing.isBom(any(), anyLong())).thenReturn(false);
        Tuple product = tuple(mapOf("code", "SKU", "name_cn", "item", "spec", "S", "tax_rate", new BigDecimal("0.13"), "product_line_id", null));
        when(pricing.product(any(), anyLong())).thenReturn(product);
        when(pricing.salesPrice(any(), eq(2L), eq(dealerId), eq(V4PricingService.PriceUse.STANDALONE), isNull()))
                .thenReturn(new V4PricingService.Price(new BigDecimal("884.9558"), new BigDecimal("0.13"), new BigDecimal("1000")));
        when(pricing.salesPrice(any(), eq(1L), eq(dealerId), eq(V4PricingService.PriceUse.STANDALONE), isNull()))
                .thenReturn(new V4PricingService.Price(new BigDecimal("442.4779"), new BigDecimal("0.13"), new BigDecimal("500")));

        V4CalcResult result = calculator.expand(tid, dealerId, List.of(
                Map.of("productId", 2L, "qty", new BigDecimal("1"), "lineDiscountType", "PERCENT", "lineDiscountValue", new BigDecimal("80")),
                Map.of("productId", 1L, "qty", new BigDecimal("1"))
        ), false, "AMOUNT", new BigDecimal("270"));

        List<V4Line> paid = result.getLines().stream().filter(l -> !l.isGift() && !"PARENT".equals(l.getLineLevel())).toList();
        assertThat(paid).hasSize(2);
        assertThat(paid.get(0).getLineDiscountAmount()).isEqualByComparingTo("200.00");
        assertThat(paid.get(0).getHeaderDiscountAmount()).isEqualByComparingTo("166.15");
        assertThat(paid.get(0).getFinalAmount()).isEqualByComparingTo("633.85");
        assertThat(paid.get(1).getHeaderDiscountAmount()).isEqualByComparingTo("103.85");
        assertThat(paid.get(1).getFinalAmount()).isEqualByComparingTo("396.15");
        assertThat(paid.stream().map(V4Line::getFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1030.00");
    }

    private Map<String,Object> mapOf(Object... kv) { java.util.Map<String,Object> m=new java.util.HashMap<>(); for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]); return m; }

    @SuppressWarnings("unchecked")
    private Tuple tuple(Map<String, Object> values) {
        Tuple tuple = org.mockito.Mockito.mock(Tuple.class);
        values.forEach((k, v) -> lenient().when(tuple.get(k)).thenReturn(v));
        return tuple;
    }
}