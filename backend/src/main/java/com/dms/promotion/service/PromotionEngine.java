/*
 * 促销引擎：evaluate(tenantId, dealerId, lines[]) 返回命中、赠品、折扣、警告。
 * 处理 MOQ + FULL_REDUCTION 两种类型，按 priority DESC/createdAt ASC 排序；
 * exclusive=true 时同类型只保留最高优先级。
 */
package com.dms.promotion.service;

import com.dms.promotion.dto.PromotionEvaluationResult;
import com.dms.promotion.dto.PromotionHit;
import com.dms.promotion.dto.PromotionLine;
import com.dms.promotion.entity.Promotion;
import com.dms.promotion.entity.PromotionRule;
import com.dms.promotion.repository.PromotionRepository;
import com.dms.promotion.repository.PromotionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionEngine {

    private final PromotionRepository promotionRepository;
    private final PromotionRuleRepository ruleRepository;

    /**
     * 主入口。
     */
    public PromotionEvaluationResult evaluate(UUID tenantId, Long dealerId, List<PromotionLine> lines) {
        PromotionEvaluationResult result = PromotionEvaluationResult.empty();
        if (tenantId == null || lines == null || lines.isEmpty()) {
            return result;
        }

        // Step 1: 查询候选促销（active + valid 时间）
        List<Promotion> candidates = promotionRepository.findActive(tenantId, OffsetDateTime.now());

        // Step 2: dealer_scope / product_scope 过滤 + 排序
        Comparator<Promotion> cmp = Comparator
                .<Promotion>comparingInt(p -> p.getPriority() == null ? 50 : p.getPriority())
                .reversed()
                .thenComparing((Promotion p) -> p.getCreatedAt() == null ? OffsetDateTime.MIN : p.getCreatedAt());
        List<Promotion> filtered = candidates.stream()
                .filter(p -> matchDealerScope(p, dealerId))
                .filter(p -> matchProductScope(p, lines))
                .sorted(cmp)
                .toList();

        // Step 3: 累加计算 + exclusive 处理
        Set<String> exclusiveTypeUsed = new HashSet<>();
        BigDecimal totalAmount = lines.stream()
                .map(l -> l.getSubTotal() == null ? BigDecimal.ZERO : l.getSubTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Promotion p : filtered) {
            boolean isExclusive = Boolean.TRUE.equals(p.getExclusive());
            if (isExclusive && exclusiveTypeUsed.contains(p.getPromoType())) {
                continue;
            }
            List<PromotionRule> rules = ruleRepository.findByPromotionIdOrderBySeqAsc(p.getId());
            switch (p.getPromoType()) {
                case "MOQ" -> applyMoq(p, rules, lines, result);
                case "FULL_REDUCTION" -> applyFullReduction(p, rules, totalAmount, result);
                default -> log.warn("促销引擎跳过未支持的类型: {}", p.getPromoType());
            }
            if (isExclusive) exclusiveTypeUsed.add(p.getPromoType());
        }
        return result;
    }

    /**
     * MOQ 规则：单品最小起订量。qty < min_qty → BLOCK 拒单+warning，WARN 只警告。
     */
    private void applyMoq(Promotion p, List<PromotionRule> rules,
                          List<PromotionLine> lines, PromotionEvaluationResult result) {
        for (PromotionRule rule : rules) {
            Map<String, Object> detail = rule.getRuleDetail();
            if (detail == null) continue;
            Long productId = toLong(detail.get("productId"));
            BigDecimal minQty = toBigDecimal(detail.get("minQty"));
            String mode = detail.get("mode") == null ? "WARN" : String.valueOf(detail.get("mode"));
            if (productId == null || minQty == null) continue;
            for (PromotionLine line : lines) {
                if (!productId.equals(line.getProductId())) continue;
                BigDecimal qty = line.getQty() == null ? BigDecimal.ZERO : line.getQty();
                if (qty.compareTo(minQty) < 0) {
                    String msg = String.format("商品 %s 数量 %s 低于最小起订量 %s (promo=%s)",
                            productId, qty, minQty, p.getCode());
                    if ("BLOCK".equalsIgnoreCase(mode)) {
                        result.setRejected(true);
                        result.getRejectedReasons().add(msg);
                    }
                    result.getWarnings().add(msg);
                    result.getHits().add(PromotionHit.builder()
                            .promotionId(p.getId())
                            .promoType("MOQ")
                            .discount(BigDecimal.ZERO)
                            .detail(msg)
                            .build());
                }
            }
        }
    }

    /**
     * FULL_REDUCTION：按订单总额落到最高档扣减（tiers 按 amount 由低到高，取最后一个 amount<=total 的档）。
     */
    private void applyFullReduction(Promotion p, List<PromotionRule> rules,
                                     BigDecimal totalAmount, PromotionEvaluationResult result) {
        for (PromotionRule rule : rules) {
            Map<String, Object> detail = rule.getRuleDetail();
            if (detail == null) continue;
            Object tiersObj = detail.get("tiers");
            if (!(tiersObj instanceof List<?> tiers) || tiers.isEmpty()) continue;

            BigDecimal bestReduce = BigDecimal.ZERO;
            BigDecimal bestAmount = BigDecimal.ZERO;
            for (Object t : tiers) {
                if (!(t instanceof Map<?, ?> tier)) continue;
                BigDecimal amount = toBigDecimal(tier.get("amount"));
                BigDecimal reduce = toBigDecimal(tier.get("reduce"));
                if (amount == null || reduce == null) continue;
                if (totalAmount.compareTo(amount) >= 0 && amount.compareTo(bestAmount) >= 0) {
                    bestAmount = amount;
                    bestReduce = reduce;
                }
            }
            if (bestReduce.compareTo(BigDecimal.ZERO) > 0) {
                result.setDiscountTotal(result.getDiscountTotal().add(bestReduce));
                result.getHits().add(PromotionHit.builder()
                        .promotionId(p.getId())
                        .promoType("FULL_REDUCTION")
                        .discount(bestReduce)
                        .detail(String.format("满 %s 减 %s", bestAmount, bestReduce))
                        .build());
            }
        }
    }

    private boolean matchDealerScope(Promotion p, Long dealerId) {
        Map<String, Object> scope = p.getDealerScope();
        if (scope == null || scope.isEmpty()) return true;
        Object ids = scope.get("dealerIds");
        if (!(ids instanceof List<?> list) || list.isEmpty()) return true;
        return list.stream().map(this::toLong).anyMatch(id -> id != null && id.equals(dealerId));
    }

    private boolean matchProductScope(Promotion p, List<PromotionLine> lines) {
        Map<String, Object> scope = p.getProductScope();
        if (scope == null || scope.isEmpty()) return true;
        Object ids = scope.get("productIds");
        if (!(ids instanceof List<?> list) || list.isEmpty()) return true;
        Set<Long> allowed = new HashSet<>();
        for (Object o : list) {
            Long v = toLong(o);
            if (v != null) allowed.add(v);
        }
        return lines.stream().anyMatch(l -> allowed.contains(l.getProductId()));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
