package com.dms.v4;

import jakarta.persistence.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for V4Calculator promotion/BOM interaction. No Spring context,
 * no database. V4PricingService is mocked so these run in milliseconds and pin
 * down pricing rules that historically regressed (gift accumulation, BOM parent
 * exclusion, EVERY_N gifting, full-reduction allocation).
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class V4CalculatorPromotionTest {

    @Mock
    V4PricingService pricing;
    @InjectMocks
    V4Calculator calculator;

    private static final UUID TID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long DEALER = 7L;

    private void stubProduct(Long pid, String code, String name, BigDecimal taxRate, Long productLineId) {
        Tuple t = tuple(mapOf("code", code, "name_cn", name, "spec", "S",
                "tax_rate", taxRate, "product_line_id", productLineId));
        lenient().when(pricing.product(eq(TID), eq(pid))).thenReturn(t);
    }

    private void stubStandalonePrice(Long pid, BigDecimal incl) {
        lenient().when(pricing.salesPrice(eq(TID), eq(pid), eq(DEALER),
                eq(V4PricingService.PriceUse.STANDALONE), isNull()))
                .thenReturn(new V4PricingService.Price(incl.divide(new BigDecimal("1.13"), 4, java.math.RoundingMode.HALF_UP),
                        new BigDecimal("0.13"), incl));
    }

    @Test
    void singleProduct_belowGiftThreshold_producesNoGiftAndNoReduction() {
        stubProduct(1L, "SKU1", "Item 1", new BigDecimal("0.13"), null);
        stubStandalonePrice(1L, new BigDecimal("100"));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of());

        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("3"))),
                true, null, BigDecimal.ZERO);

        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines()).noneMatch(V4Line::isGift);
        assertThat(result.getLines().get(0).getFinalAmount()).isEqualByComparingTo("300.00");
        assertThat(result.getPromotionMessages()).isEmpty();
    }

    @Test
    void moqOnce_reachesThreshold_addsOneGiftLine() {
        stubProduct(1L, "SKU1", "Item 1", new BigDecimal("0.13"), null);
        stubProduct(999L, "GIFT", "Free Gift", new BigDecimal("0.13"), null);
        stubStandalonePrice(1L, new BigDecimal("100"));
        when(pricing.isBom(TID, 1L)).thenReturn(false);
        when(pricing.isBom(TID, 999L)).thenReturn(false);
        Tuple promo = promo(902L, "MOQ", "买5赠1", null, null, null);
        Tuple rule = rule(mapOf("thresholdQty", 5, "giftProductId", 999L, "giftQty", 1, "cycle", "ONCE"));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of(promo));
        when(pricing.promotionRules(902L)).thenReturn(List.of(rule));

        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("5"))),
                true, null, BigDecimal.ZERO);
        V4Line gift = result.getLines().stream().filter(V4Line::isGift).findFirst().orElseThrow();
        assertThat(gift.getProductId()).isEqualTo(999L);
        assertThat(gift.getFinalAmount()).isEqualByComparingTo("0");
        assertThat(result.getLines()).hasSize(2);
        assertThat(String.join("|", result.getPromotionMessages())).contains("买5赠1");
    }

    @Test
    void giftEveryN_scalesWithQuantity() {
        stubProduct(1L, "SKU1", "Item 1", new BigDecimal("0.13"), null);
        stubProduct(999L, "GIFT", "Free Gift", new BigDecimal("0.13"), null);
        lenient().when(pricing.isBom(any(), anyLong())).thenReturn(false);
        stubStandalonePrice(1L, new BigDecimal("100"));
        Tuple promo = promo(903L, "GIFT", "每3赠1", null, null, null);
        // 新语义：threshold=起赠门槛A=3，everyN=循环步长=3；qty=10 => 1 + floor((10-3)/3) = 3 件赠品
        Tuple rule = rule(mapOf("thresholdQty", 3, "giftProductId", 999L, "giftQty", 1, "cycle", "EVERY_N", "everyN", 3));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of(promo));
        when(pricing.promotionRules(903L)).thenReturn(List.of(rule));

        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("10"))),
                true, null, BigDecimal.ZERO);

        V4Line gift = result.getLines().stream().filter(V4Line::isGift).findFirst().orElseThrow();
        // 达到门槛赠1 + 每再满3赠1 => qty=10 赠 1+floor(7/3)=3 件
        assertThat(gift.getQty()).isEqualByComparingTo("3");
    }

    @Test
    void giftEveryN_usesThresholdAsGateAndEveryNAsStep() {
        // 起赠门槛 A=2，循环步长 everyN=3：qty=8 => 1 + floor((8-2)/3) = 3 件赠品
        // 旧实现会算成 floor(8/2)=4，此用例锁定新语义防止回归。
        stubProduct(1L, "SKU1", "Item 1", new BigDecimal("0.13"), null);
        stubProduct(999L, "GIFT", "Free Gift", new BigDecimal("0.13"), null);
        lenient().when(pricing.isBom(any(), anyLong())).thenReturn(false);
        stubStandalonePrice(1L, new BigDecimal("100"));
        Tuple promo = promo(910L, "GIFT", "买2起每3赠1", null, null, null);
        Tuple rule = rule(mapOf("thresholdQty", 2, "giftProductId", 999L, "giftQty", 1, "cycle", "EVERY_N", "everyN", 3));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of(promo));
        when(pricing.promotionRules(910L)).thenReturn(List.of(rule));

        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("8"))),
                true, null, BigDecimal.ZERO);

        V4Line gift = result.getLines().stream().filter(V4Line::isGift).findFirst().orElseThrow();
        assertThat(gift.getQty()).isEqualByComparingTo("3");
    }

    @Test
    void fullReduction_allocatesAcrossLinesByAfterLineDiscountShare() {
        stubProduct(1L, "SKU1", "Item 1", new BigDecimal("0.13"), null);
        stubProduct(2L, "SKU2", "Item 2", new BigDecimal("0.13"), null);
        stubStandalonePrice(1L, new BigDecimal("1000"));
        stubStandalonePrice(2L, new BigDecimal("500"));
        lenient().when(pricing.isBom(any(), anyLong())).thenReturn(false);
        Tuple promo = promo(904L, "FULL_REDUCTION", "满1000减300", null, null, null);
        Tuple rule = rule(mapOf("thresholdQty", 1, "reduceAmount", 300, "discountType", "AMOUNT"));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of(promo));
        when(pricing.promotionRules(904L)).thenReturn(List.of(rule));

        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("1")),
                Map.of("productId", 2L, "qty", new BigDecimal("1"))),
                true, null, BigDecimal.ZERO);

        List<V4Line> paid = result.getLines().stream().filter(l -> !l.isGift()).toList();
        BigDecimal totalPromo = paid.stream().map(V4Line::getPromoDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 300 allocated proportional to 1000:500 => 200 + 100
        assertThat(totalPromo.setScale(2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("300.00");
        BigDecimal totalFinal = paid.stream().map(V4Line::getFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalFinal).isEqualByComparingTo("1200.00");
    }

    @Test
    void fullReduction_snakeCaseAmountThreshold_matchesSeededPromoShape() {
        // 种子数据 V7 促销 rule_detail 使用下划线 + 金额门槛：
        // {threshold_amount:10000, discount:500}
        stubProduct(1L, "SKU1", "Item 1", new BigDecimal("0.13"), null);
        stubStandalonePrice(1L, new BigDecimal("1000"));
        lenient().when(pricing.isBom(any(), anyLong())).thenReturn(false);
        Tuple promo = promo(910L, "FULL_REDUCTION", "满10000减500", null, null, null);
        Tuple rule = rule(mapOf("threshold_amount", 10000, "discount", 500, "discount_type", "AMOUNT"));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of(promo));
        when(pricing.promotionRules(910L)).thenReturn(List.of(rule));

        // 12 件 * 1000 = 12000，达到门槛，减 500
        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("12"))),
                true, null, BigDecimal.ZERO);
        List<V4Line> paid = result.getLines().stream().filter(l -> !l.isGift()).toList();
        BigDecimal totalPromo = paid.stream().map(V4Line::getPromoDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPromo.setScale(2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("500.00");
        BigDecimal totalFinal = paid.stream().map(V4Line::getFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalFinal).isEqualByComparingTo("11500.00");

        // 6 件 = 6000，未达门槛，不减免
        V4CalcResult below = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("6"))),
                true, null, BigDecimal.ZERO);
        BigDecimal belowPromo = below.getLines().stream().filter(l -> !l.isGift())
                .map(V4Line::getPromoDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(belowPromo).isEqualByComparingTo("0");
    }

    @Test
    void moqGift_snakeCaseThreshold_addsGift() {
        stubProduct(1L, "SKU1", "Item 1", new BigDecimal("0.13"), null);
        stubProduct(999L, "GIFT", "Free Gift", new BigDecimal("0.13"), null);
        stubStandalonePrice(1L, new BigDecimal("100"));
        lenient().when(pricing.isBom(any(), anyLong())).thenReturn(false);
        Tuple promo = promo(911L, "GIFT", "买10赠1", null, null, null);
        Tuple rule = rule(mapOf("threshold_qty", 10, "gift_product_id", 999, "gift_qty", 1));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of(promo));
        when(pricing.promotionRules(911L)).thenReturn(List.of(rule));

        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("10"))),
                true, null, BigDecimal.ZERO);
        V4Line gift = result.getLines().stream().filter(V4Line::isGift).findFirst().orElseThrow();
        assertThat(gift.getProductId()).isEqualTo(999L);
    }
    @Test
    void bomParent_isExcludedFromPromotionAndTotal() {
        when(pricing.isBom(TID, 500L)).thenReturn(true);
        when(pricing.currentBomVersion(TID, 500L)).thenReturn("1");
        when(pricing.bomLines(TID, 500L, "1")).thenReturn(List.of(
                Map.of("productId", 501L, "code", "C1", "name", "child", "spec", "S",
                        "quantity", new BigDecimal("2"), "bomVersion", "1", "serialManaged", false)));
        stubProduct(500L, "BOM", "parent", BigDecimal.ZERO, null);
        stubProduct(501L, "C1", "child", new BigDecimal("0.13"), null);
        when(pricing.salesPrice(eq(TID), eq(501L), eq(DEALER), eq(V4PricingService.PriceUse.BOM_COMPONENT), eq(500L)))
                .thenReturn(new V4PricingService.Price(new BigDecimal("88.4956"), new BigDecimal("0.13"), new BigDecimal("100")));
        when(pricing.activePromotions(TID, DEALER)).thenReturn(List.of());
        when(pricing.promotionRules(anyLong())).thenReturn(List.of());

        V4CalcResult result = calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 500L, "qty", new BigDecimal("1"), "bomVersion", "1")),
                true, null, BigDecimal.ZERO);

        V4Line parent = result.getLines().stream().filter(l -> "PARENT".equals(l.getLineLevel())).findFirst().orElseThrow();
        V4Line child = result.getLines().stream().filter(l -> "CHILD".equals(l.getLineLevel())).findFirst().orElseThrow();
        assertThat(parent.getFinalAmount()).isEqualByComparingTo("0");
        assertThat(parent.isGroupHeader()).isTrue();
        // qty 1 * component qty 2 * unit 100 = 200
        assertThat(child.getQty()).isEqualByComparingTo("2");
        assertThat(child.getFinalAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void zeroQtyLine_isRejected() {
        assertThatThrownBy(() -> calculator.expand(TID, DEALER, List.of(
                Map.of("productId", 1L, "qty", new BigDecimal("0"))), false, null, BigDecimal.ZERO))
                .hasMessageContaining("数量必须大于0");
    }

    private Tuple promo(Long id, String type, String name, Object dealerScope, Object productScope, Object rules) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("code", name);
        m.put("promo_type", type);
        m.put("dealer_scope", dealerScope);
        m.put("product_scope", productScope);
        return tuple(m);
    }

    private Tuple rule(Map<String, Object> detail) {
        return tuple(mapOf("rule_detail", detail));
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Tuple tuple(Map<String, Object> values) {
        Tuple tuple = org.mockito.Mockito.mock(Tuple.class);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            lenient().when(tuple.get(e.getKey())).thenReturn(e.getValue());
        }
        return tuple;
    }
}

