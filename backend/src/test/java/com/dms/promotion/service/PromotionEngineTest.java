/*
 * 测试目标：验证 PromotionEngine 促销引擎的 MOQ BLOCK/WARN、FULL_REDUCTION 分档、
 *          exclusive 互斥、priority 排序等核心行为。
 * 覆盖用户故事：US-6.1（MOQ 校验）、US-6.2（满减 FULL_REDUCTION）、US-6.5（互斥优先级）。
 */
package com.dms.promotion.service;

import com.dms.promotion.dto.PromotionEvaluationResult;
import com.dms.promotion.dto.PromotionLine;
import com.dms.promotion.entity.Promotion;
import com.dms.promotion.entity.PromotionRule;
import com.dms.promotion.repository.PromotionRepository;
import com.dms.promotion.repository.PromotionRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PromotionEngine 纯 Mockito 单元测试，不启动 Spring。
 */
class PromotionEngineTest {

    private PromotionRepository promotionRepository;
    private PromotionRuleRepository ruleRepository;
    private PromotionEngine engine;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        promotionRepository = mock(PromotionRepository.class);
        ruleRepository = mock(PromotionRuleRepository.class);
        engine = new PromotionEngine(promotionRepository, ruleRepository);
        tenantId = UUID.randomUUID();
    }

    private Promotion buildPromo(long id, String type, int priority, boolean exclusive) {
        Promotion p = new Promotion();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setCode("P-" + id);
        p.setName("促销" + id);
        p.setPromoType(type);
        p.setPriority(priority);
        p.setStatus("active");
        p.setExclusive(exclusive);
        p.setCreatedAt(OffsetDateTime.now());
        return p;
    }

    private PromotionRule moqRule(long promoId, long productId, int minQty, String mode) {
        PromotionRule r = new PromotionRule();
        r.setId(promoId * 10);
        r.setPromotionId(promoId);
        r.setSeq(1);
        Map<String, Object> detail = new HashMap<>();
        detail.put("productId", productId);
        detail.put("minQty", minQty);
        detail.put("mode", mode);
        r.setRuleDetail(detail);
        return r;
    }

    private PromotionRule fullReductionRule(long promoId, List<Map<String, Object>> tiers) {
        PromotionRule r = new PromotionRule();
        r.setId(promoId * 10);
        r.setPromotionId(promoId);
        r.setSeq(1);
        Map<String, Object> detail = new HashMap<>();
        detail.put("tiers", tiers);
        r.setRuleDetail(detail);
        return r;
    }

    @Test
    @DisplayName("正常流程：无候选促销时返回空结果")
    void should_returnEmpty_when_noActivePromotions() {
        when(promotionRepository.findActive(eq(tenantId), any(OffsetDateTime.class)))
                .thenReturn(new ArrayList<>());

        List<PromotionLine> lines = List.of(
                new PromotionLine(1L, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("50")));
        PromotionEvaluationResult result = engine.evaluate(tenantId, 100L, lines);

        assertThat(result.getHits()).isEmpty();
        assertThat(result.getDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getRejected()).isFalse();
    }

    @Test
    @DisplayName("MOQ BLOCK 模式：qty 低于 minQty 时拒单")
    void should_rejectOrder_when_moqBlockAndBelowMinQty() {
        Promotion p = buildPromo(1L, "MOQ", 50, false);
        when(promotionRepository.findActive(eq(tenantId), any())).thenReturn(List.of(p));
        when(ruleRepository.findByPromotionIdOrderBySeqAsc(1L))
                .thenReturn(List.of(moqRule(1L, 100L, 10, "BLOCK")));

        List<PromotionLine> lines = List.of(
                new PromotionLine(100L, new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("100")));
        PromotionEvaluationResult r = engine.evaluate(tenantId, 200L, lines);

        assertThat(r.getRejected()).isTrue();
        assertThat(r.getRejectedReasons()).isNotEmpty();
        assertThat(r.getWarnings()).hasSize(1);
        assertThat(r.getHits()).hasSize(1);
    }

    @Test
    @DisplayName("MOQ WARN 模式：仅生成 warning，不拒单")
    void should_warnOnly_when_moqWarnModeBelowMinQty() {
        Promotion p = buildPromo(2L, "MOQ", 50, false);
        when(promotionRepository.findActive(eq(tenantId), any())).thenReturn(List.of(p));
        when(ruleRepository.findByPromotionIdOrderBySeqAsc(2L))
                .thenReturn(List.of(moqRule(2L, 100L, 10, "WARN")));

        List<PromotionLine> lines = List.of(
                new PromotionLine(100L, new BigDecimal("3"), new BigDecimal("20"), new BigDecimal("60")));
        PromotionEvaluationResult r = engine.evaluate(tenantId, 200L, lines);

        assertThat(r.getRejected()).isFalse();
        assertThat(r.getWarnings()).hasSize(1);
    }

    @Test
    @DisplayName("FULL_REDUCTION：按订单总额落到最高档扣减")
    void should_applyHighestTier_when_fullReductionMultipleTiers() {
        Promotion p = buildPromo(3L, "FULL_REDUCTION", 50, false);
        when(promotionRepository.findActive(eq(tenantId), any())).thenReturn(List.of(p));

        List<Map<String, Object>> tiers = new ArrayList<>();
        tiers.add(Map.of("amount", 1000, "reduce", 50));
        tiers.add(Map.of("amount", 2000, "reduce", 150));
        tiers.add(Map.of("amount", 5000, "reduce", 500));
        when(ruleRepository.findByPromotionIdOrderBySeqAsc(3L))
                .thenReturn(List.of(fullReductionRule(3L, tiers)));

        // 订单总额 2500，落到第二档 reduce=150
        List<PromotionLine> lines = List.of(
                new PromotionLine(1L, new BigDecimal("5"), new BigDecimal("500"),
                        new BigDecimal("2500")));
        PromotionEvaluationResult r = engine.evaluate(tenantId, 200L, lines);

        assertThat(r.getDiscountTotal()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(r.getHits()).hasSize(1);
        assertThat(r.getHits().get(0).getPromoType()).isEqualTo("FULL_REDUCTION");
    }

    @Test
    @DisplayName("FULL_REDUCTION：订单未达最低档时不扣减")
    void should_notApply_when_totalBelowLowestTier() {
        Promotion p = buildPromo(4L, "FULL_REDUCTION", 50, false);
        when(promotionRepository.findActive(eq(tenantId), any())).thenReturn(List.of(p));
        when(ruleRepository.findByPromotionIdOrderBySeqAsc(4L))
                .thenReturn(List.of(fullReductionRule(4L,
                        List.of(Map.of("amount", 1000, "reduce", 100)))));

        List<PromotionLine> lines = List.of(
                new PromotionLine(1L, new BigDecimal("1"), new BigDecimal("500"),
                        new BigDecimal("500")));
        PromotionEvaluationResult r = engine.evaluate(tenantId, 200L, lines);

        assertThat(r.getDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.getHits()).isEmpty();
    }

    @Test
    @DisplayName("exclusive 互斥：同类型只保留最高优先级的一条")
    void should_keepOnlyHighestPriority_when_exclusiveSameType() {
        Promotion high = buildPromo(10L, "FULL_REDUCTION", 90, true);
        Promotion low = buildPromo(11L, "FULL_REDUCTION", 30, true);
        when(promotionRepository.findActive(eq(tenantId), any()))
                .thenReturn(List.of(high, low));
        when(ruleRepository.findByPromotionIdOrderBySeqAsc(10L))
                .thenReturn(List.of(fullReductionRule(10L,
                        List.of(Map.of("amount", 1000, "reduce", 200)))));
        when(ruleRepository.findByPromotionIdOrderBySeqAsc(11L))
                .thenReturn(List.of(fullReductionRule(11L,
                        List.of(Map.of("amount", 1000, "reduce", 100)))));

        List<PromotionLine> lines = List.of(
                new PromotionLine(1L, new BigDecimal("5"), new BigDecimal("500"),
                        new BigDecimal("2500")));
        PromotionEvaluationResult r = engine.evaluate(tenantId, 200L, lines);

        assertThat(r.getHits()).hasSize(1);
        assertThat(r.getHits().get(0).getPromotionId()).isEqualTo(10L);
        assertThat(r.getDiscountTotal()).isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    @DisplayName("边界：空 lines 直接返回 empty 结果，不做任何仓储查询")
    void should_returnEmpty_when_linesIsEmpty() {
        PromotionEvaluationResult r = engine.evaluate(tenantId, 1L, List.of());
        assertThat(r.getHits()).isEmpty();
        assertThat(r.getRejected()).isFalse();
    }
}
