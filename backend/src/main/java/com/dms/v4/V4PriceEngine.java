package com.dms.v4;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * v4.3.0 订单计价引擎。严格按《订单折扣与促销规则说明书》第 2 节顺序：
 * 基础价(合同>客户>全局) -> 产品全局折扣 -> 促销 -> 行手动折扣 -> 行金额
 * -> 客户全局折扣 -> 整单手动折扣 -> EA 分摊 -> 税额。
 * 全部含税；数量为正整数；单价/金额精确到分，尾差落金额最大行。
 */
@Service
@RequiredArgsConstructor
public class V4PriceEngine {
    private static final Logger log = LoggerFactory.getLogger(V4PriceEngine.class);
    private final V4PricingService pricing;

    public V4CalcResult calculate(UUID tid, Long dealerId, List<Map<String, Object>> requestLines,
                                  boolean applyPromotions, Map<String, Object> params) {
        String mode = str(params.get("pricingMode"), "NORMAL").toUpperCase(Locale.ROOT);
        if (!List.of("NORMAL", "FIXED_PRICE", "ZERO_ORDER", "VOUCHER").contains(mode)) mode = "NORMAL";
        String headerDiscountType = str(params.get("headerDiscountType"), null);
        BigDecimal headerDiscountValue = bd(params.get("headerDiscountValue"));
        String headerDiscountDirection = str(params.get("headerDiscountDirection"), null);
        BigDecimal fixedPrice = bd(params.get("fixedPrice"));

        List<V4Line> lines = new ArrayList<>();
        int lineNo = 0;
        for (Map<String, Object> row : requestLines) {
            Long pid = toLong(row.get("productId"));
            if (pid == null) continue;
            if (Boolean.TRUE.equals(row.get("isGift")) || "CHILD".equals(str(row.get("lineLevel"), null))) continue;
            lineNo++;
            BigDecimal qty = requirePositiveInt(row.get("qty"), lineNo);
            boolean lineZero = Boolean.TRUE.equals(row.get("lineZero"));
            if (pricing.isBom(tid, pid)) {
                expandBom(tid, dealerId, pid, qty, row, lines, lineNo, lineZero);
            } else {
                Tuple prod = pricing.product(tid, pid);
                V4Line l = buildStandalone(tid, dealerId, pid, qty, prod);
                l.setLineZero(lineZero);
                l.setLineDiscountType(str(row.get("lineDiscountType"), null));
                l.setLineDiscountValue(bd(row.get("lineDiscountValue")));
                l.setLineDiscountDirection(str(row.get("lineDiscountDirection"), null));
                // v4.4.0 开票订单：透传寄售库存批号/序列号，供订单行落库与寄售预占/实扣匹配
                l.setBatchNo(str(row.get("batchNo"), null));
                l.setSerialNo(str(row.get("serialNo"), null));
                // v4.4.1 透传用户在拣选弹窗中勾选的具体台账行 id（精准锁定）
                l.setConsignmentStockId(toLong(row.get("consignmentStockId")));
                lines.add(l);
            }
        }
        for (V4Line l : lines) l.setLineLabel(label(l));

        // 互斥模式前置校验：非 NORMAL 不允许任何折扣输入。
        validateModeInputs(mode, lines, headerDiscountType, headerDiscountValue);

        List<V4Line> chargeable = lines.stream().filter(this::isChargeable).toList();

        V4CalcResult result = V4CalcResult.of(lines, new ArrayList<>());
        result.setPricingMode(mode);
        result.setHeaderDiscountType(headerDiscountType);
        result.setHeaderDiscountValue(headerDiscountValue);
        result.setHeaderDiscountDirection(headerDiscountDirection);

        if (mode.equals("ZERO_ORDER")) {
            zeroOrder(chargeable);
        } else if (mode.equals("FIXED_PRICE")) {
            fixedPriceMode(tid, chargeable, fixedPrice);
        } else if (mode.equals("VOUCHER")) {
            voucherBasePrice(chargeable);
        } else {
            normalMode(tid, dealerId, chargeable, applyPromotions, result,
                    headerDiscountType, headerDiscountValue, headerDiscountDirection);
        }

        finalizeUnitPrices(chargeable);
        result.setFixedPrice("FIXED_PRICE".equals(mode) ? fixedPrice : null);
        applyVoucher(tid, mode, params, chargeable, result);
        aggregate(result, lines, mode);
        validateNonNegative(lines, mode, result.getVoucherAmount());
        return result;
    }

    // ---- 行展开 ----

    private int expandBom(UUID tid, Long dealerId, Long pid, BigDecimal qty, Map<String, Object> row,
                          List<V4Line> lines, int startNo, boolean parentLineZero) {
        String group = "BOM-" + pid + "-" + startNo;
        String version = str(row.get("bomVersion"), pricing.currentBomVersion(tid, pid));
        List<Map<String, Object>> comps = pricing.bomLines(tid, pid, version);
        Tuple parent = pricing.product(tid, pid);
        lines.add(V4Line.builder()
                .productId(pid).productCode(parent == null ? "" : str(parent.get("code"), ""))
                .productName(parent == null ? "" : firstText(parent.get("name_cn"), parent.get("code")))
                .productSpec(parent == null ? "" : str(parent.get("spec"), ""))
                .qty(qty).componentQty(BigDecimal.ONE).taxRate(BigDecimal.ZERO)
                .unitPriceExclTax(BigDecimal.ZERO).standardPriceInclTax(BigDecimal.ZERO).basePriceInclTax(BigDecimal.ZERO)
                .standardAmount(BigDecimal.ZERO).finalAmount(BigDecimal.ZERO).unitPriceInclTax(BigDecimal.ZERO)
                .lineDiscountAmount(BigDecimal.ZERO).promoDiscountAmount(BigDecimal.ZERO).headerDiscountAmount(BigDecimal.ZERO)
                .productDiscountAmount(BigDecimal.ZERO).dealerDiscountAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .amountExclTax(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO)
                .bomVersion(version).bomGroupNo(group).lineLevel("PARENT").groupHeader(true).build());
        Map<Long, Map<String, Object>> childDiscounts = childDiscounts(row);
        int added = 0;
        for (Map<String, Object> c : comps) {
            Long childId = toLong(c.get("productId"));
            if (pid.equals(childId)) continue;
            BigDecimal compQty = requirePositiveInt(c.get("quantity"), startNo);
            V4Line l = buildBomComponent(tid, dealerId, childId, qty.multiply(compQty), c, pid, version, group);
            Map<String, Object> d = childDiscounts.get(childId);
            if (d != null) {
                l.setLineDiscountType(str(d.get("lineDiscountType"), null));
                l.setLineDiscountValue(bd(d.get("lineDiscountValue")));
                l.setLineDiscountDirection(str(d.get("lineDiscountDirection"), null));
            }
            // v4.4.5：补货/样品/整单0 等零金额订单，BOM 子件行同步置零，避免子件仍计价导致整单金额不为 0
            if (parentLineZero) {
                l.setLineZero(true);
                l.setLineDiscountType(null);
                l.setLineDiscountValue(null);
            }
            lines.add(l);
            added++;
        }
        return added;
    }

    private V4Line buildStandalone(UUID tid, Long dealerId, Long pid, BigDecimal qty, Tuple prod) {
        V4PricingService.SourcedPrice sp = pricing.basePrice(tid, pid, dealerId);
        BigDecimal base = money(sp.price().incl());
        BigDecimal std = money(base.multiply(qty));
        return V4Line.builder()
                .productId(pid)
                .productCode(prod == null ? "" : str(prod.get("code"), ""))
                .productName(prod == null ? "" : firstText(prod.get("name_cn"), prod.get("code")))
                .productSpec(prod == null ? "" : str(prod.get("spec"), ""))
                .qty(qty).componentQty(BigDecimal.ONE)
                .taxRate(sp.price().rate()).unitPriceExclTax(sp.price().excl())
                .standardPriceInclTax(base).basePriceInclTax(base).unitPriceInclTax(base)
                .priceSource(sp.source()).standardAmount(std)
                .lineDiscountAmount(BigDecimal.ZERO).promoDiscountAmount(BigDecimal.ZERO).headerDiscountAmount(BigDecimal.ZERO)
                .productDiscountAmount(BigDecimal.ZERO).dealerDiscountAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .finalAmount(std).lineLevel("NORMAL").build();
    }

    private V4Line buildBomComponent(UUID tid, Long dealerId, Long pid, BigDecimal qty, Map<String, Object> comp,
                                     Long bomParent, String version, String group) {
        V4PricingService.Price price = pricing.salesPrice(tid, pid, dealerId, V4PricingService.PriceUse.BOM_COMPONENT, bomParent);
        BigDecimal std = money(price.incl().multiply(qty));
        return V4Line.builder()
                .productId(pid).productCode(str(comp.get("code"), "")).productName(str(comp.get("name"), ""))
                .productSpec(str(comp.get("spec"), ""))
                .qty(qty).componentQty(BigDecimal.ONE).taxRate(price.rate()).unitPriceExclTax(price.excl())
                .standardPriceInclTax(price.incl()).basePriceInclTax(price.incl()).unitPriceInclTax(price.incl())
                .priceSource("BOM").standardAmount(std)
                .lineDiscountAmount(BigDecimal.ZERO).promoDiscountAmount(BigDecimal.ZERO).headerDiscountAmount(BigDecimal.ZERO)
                .productDiscountAmount(BigDecimal.ZERO).dealerDiscountAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .finalAmount(std).bomParentProductId(bomParent).bomVersion(version).bomGroupNo(group)
                .lineLevel("CHILD").build();
    }

    private V4Line buildGiftLine(UUID tid, Long giftProductId, BigDecimal qty) {
        Tuple p = pricing.product(tid, giftProductId);
        return V4Line.builder().productId(giftProductId)
                .productCode(p == null ? "" : str(p.get("code"), ""))
                .productName(p == null ? "" : str(p.get("name_cn"), ""))
                .productSpec(p == null ? "" : str(p.get("spec"), ""))
                .qty(qty).componentQty(BigDecimal.ONE).taxRate(BigDecimal.ZERO)
                .unitPriceExclTax(BigDecimal.ZERO).standardPriceInclTax(BigDecimal.ZERO).basePriceInclTax(BigDecimal.ZERO)
                .standardAmount(BigDecimal.ZERO).finalAmount(BigDecimal.ZERO).unitPriceInclTax(BigDecimal.ZERO)
                .lineDiscountAmount(BigDecimal.ZERO).promoDiscountAmount(BigDecimal.ZERO).headerDiscountAmount(BigDecimal.ZERO)
                .productDiscountAmount(BigDecimal.ZERO).dealerDiscountAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .amountExclTax(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO)
                .gift(true).lineLevel("NORMAL").build();
    }

    // ---- 模式与行级计算 ----

    private void normalMode(UUID tid, Long dealerId, List<V4Line> chargeable, boolean applyPromotions,
                            V4CalcResult result, String headerType, BigDecimal headerValue, String headerDir) {
        // 促销 SKU 不可手动 0；同 SKU 拆多行拦截。
        Set<Long> promoSkus = applyPromotions ? collectPromoSkuIds(tid, dealerId, chargeable) : Set.of();
        for (V4Line l : chargeable) {
            if (l.isLineZero() && promoSkus.contains(l.getProductId())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "第 [" + l.getLineLabel() + "]：该产品处于促销中，不能设为 0 金额行");
            }
        }
        detectDuplicateSkuRows(chargeable);

        BigDecimal dealerRate = pricing.dealerGlobalDiscountRate(tid, dealerId);

        for (V4Line l : chargeable) {
            if (l.isLineZero()) { zeroLine(l); continue; }
            // 2. 产品全局折扣（只减）
            BigDecimal prodRate = pricing.productGlobalDiscountRate(tid, l.getProductId());
            BigDecimal prodAmt = money(l.getStandardAmount().multiply(prodRate));
            l.setProductDiscountRate(prodRate);
            l.setProductDiscountAmount(prodAmt);
            l.setFinalAmount(lineAfterProductDiscount(l));
            setTax(l);
        }

        // 3. 促销（QTY_DISCOUNT / QTY_REDUCE / GIFT）
        if (applyPromotions) applyPromotions(tid, dealerId, chargeable, result);

        // 4. 行手动折扣（QTY_DISCOUNT 命中行禁用）
        for (V4Line l : chargeable) {
            if (l.isLineZero()) continue;
            if ("QTY_DISCOUNT".equals(l.getPromoType()) && l.getLineDiscountValue() != null && l.getLineDiscountValue().signum() != 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "第 [" + l.getLineLabel() + "]：已命中满件打折促销，该行不允许再使用行手动折扣");
            }
            if ("QTY_DISCOUNT".equals(l.getPromoType())) continue;
            BigDecimal lineSigned = V4Money.signedDiscount(l.getFinalAmount(), l.getLineDiscountType(), l.getLineDiscountValue(), l.getLineDiscountDirection());
            l.setLineDiscountAmount(lineSigned);
            l.setFinalAmount(money(l.getFinalAmount().add(lineSigned)));
            if (l.getFinalAmount().signum() < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "第 [" + l.getLineLabel() + "]：行折扣后金额小于 0，请减少行减额或加价幅度过大");
            }
            setTax(l);
        }

        // 6. 客户全局折扣（整单级，只减，按行金额占比摊回行）
        if (dealerRate.signum() > 0) {
            List<BigDecimal> weights = chargeable.stream().map(this::lineWeight).toList();
            BigDecimal total = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal delta = money(total.multiply(dealerRate)).negate();
            BigDecimal[] shares = V4Money.allocate(delta, weights);
            for (int i = 0; i < chargeable.size(); i++) {
                V4Line l = chargeable.get(i);
                l.setDealerDiscountAmount(money(shares[i].abs()));
                l.setFinalAmount(money(l.getFinalAmount().add(shares[i])));
                if (l.getFinalAmount().signum() < 0) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "第 [" + l.getLineLabel() + "]：客户全局折扣后金额小于 0，请减少折扣");
                }
                setTax(l);
            }
        }

        // 7. 整单手动折扣（可加可减，按行金额占比分摊）
        BigDecimal headerSigned = V4Money.signedDiscount(orderTotal(chargeable), headerType, headerValue, headerDir);
        if (headerSigned.signum() != 0) {
            List<BigDecimal> weights = chargeable.stream().map(this::lineWeight).toList();
            BigDecimal[] shares = V4Money.allocate(headerSigned, weights);
            for (int i = 0; i < chargeable.size(); i++) {
                V4Line l = chargeable.get(i);
                l.setHeaderDiscountAmount(shares[i]);
                l.setFinalAmount(money(l.getFinalAmount().add(shares[i])));
                if (l.getFinalAmount().signum() < 0) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "第 [" + l.getLineLabel() + "]：整单折扣后金额小于 0，请减少整单减额");
                }
                setTax(l);
            }
        }
    }

    private void zeroLine(V4Line l) {
        l.setProductDiscountRate(BigDecimal.ZERO);
        l.setProductDiscountAmount(BigDecimal.ZERO);
        l.setLineDiscountAmount(BigDecimal.ZERO);
        l.setPromoDiscountAmount(BigDecimal.ZERO);
        l.setDealerDiscountAmount(BigDecimal.ZERO);
        l.setHeaderDiscountAmount(BigDecimal.ZERO);
        l.setDiscountAmount(l.getStandardAmount());
        l.setFinalAmount(BigDecimal.ZERO);
        l.setUnitPriceInclTax(BigDecimal.ZERO);
        setTax(l);
    }

    private void zeroOrder(List<V4Line> chargeable) {
        for (V4Line l : chargeable) zeroLine(l);
    }

    /** 一口价：产品按基础原价，差额按行金额占比摊到行与 EA，合计=一口价。 */
    private void fixedPriceMode(UUID tid, List<V4Line> chargeable, BigDecimal fixedPrice) {
        if (fixedPrice == null || fixedPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "一口价金额必须大于等于 0");
        }
        BigDecimal total = orderTotal(chargeable);
        BigDecimal delta = money(fixedPrice.subtract(total)); // 正=加价，负=减
        if (delta.signum() != 0) {
            List<BigDecimal> weights = chargeable.stream().map(this::lineWeight).toList();
            BigDecimal[] shares = V4Money.allocate(delta, weights);
            for (int i = 0; i < chargeable.size(); i++) {
                V4Line l = chargeable.get(i);
                l.setHeaderDiscountAmount(shares[i]);
                l.setFinalAmount(money(l.getFinalAmount().add(shares[i])));
                if (l.getFinalAmount().signum() < 0) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "第 [" + l.getLineLabel() + "]：一口价分摊后金额小于 0，请调高一口价");
                }
                setTax(l);
            }
        }
    }

    /** 代金券模式：产品按基础原价，不做任何折扣，券在订单层抵扣（不摊入单价）。 */
    private void voucherBasePrice(List<V4Line> chargeable) {
        for (V4Line l : chargeable) {
            l.setFinalAmount(l.getStandardAmount());
            l.setUnitPriceInclTax(l.getBasePriceInclTax());
            setTax(l);
        }
    }

    private void validateModeInputs(String mode, List<V4Line> lines, String headerType, BigDecimal headerValue) {
        if (mode.equals("NORMAL")) return;
        for (V4Line l : lines) {
            if (!isChargeable(l)) continue;
            if (l.isLineZero()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        (mode.equals("ZERO_ORDER") ? "整单0金额" : mode.equals("FIXED_PRICE") ? "一口价" : "代金券")
                                + "模式下不能同时设置行 0 金额：第 [" + label(l) + "]");
            }
            if (l.getLineDiscountValue() != null && l.getLineDiscountValue().signum() != 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        (mode.equals("ZERO_ORDER") ? "整单0金额" : mode.equals("FIXED_PRICE") ? "一口价" : "代金券")
                                + "模式下不能使用行折扣：第 [" + label(l) + "]");
            }
        }
        if (headerValue != null && headerValue.signum() != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    (mode.equals("ZERO_ORDER") ? "整单0金额" : mode.equals("FIXED_PRICE") ? "一口价" : "代金券")
                            + "模式下不能使用整单折扣");
        }
    }


    // ---- 促销 ----

    private Set<Long> collectPromoSkuIds(UUID tid, Long dealerId, List<V4Line> chargeable) {
        Set<Long> skus = new HashSet<>();
        Set<Long> orderSkus = new HashSet<>();
        for (V4Line l : chargeable) orderSkus.add(l.getProductId());
        for (Tuple promo : pricing.activePromotions(tid, dealerId)) {
            String type = str(promo.get("promo_type"), "");
            for (Tuple ruleT : pricing.promotionRules(toLong(promo.get("id")))) {
                Map<String, Object> detail = toMap(ruleT.get("rule_detail"));
                String scope = str(detail.get("scope") != null ? detail.get("scope") : detail.get("targetType"), "SKU");
                Object pid = detail.get("targetProductId");
                Object cid = detail.get("targetProductLineId") != null ? detail.get("targetProductLineId") : detail.get("targetCategoryId");
                Set<Long> targets;
                if ("CATEGORY".equalsIgnoreCase(scope) || "LINE".equalsIgnoreCase(scope)) {
                    targets = pricing.expandScopeToSkus(tid, null, cid);
                } else {
                    targets = pricing.expandScopeToSkus(tid, pid, null);
                }
                for (Long t : targets) if (orderSkus.contains(t)) skus.add(t);
            }
        }
        return skus;
    }

    private void applyPromotions(UUID tid, Long dealerId, List<V4Line> chargeable, V4CalcResult result) {
        Map<Long, BigDecimal> giftQtys = new LinkedHashMap<>();
        for (Tuple promo : pricing.activePromotions(tid, dealerId)) {
            if (!dealerScopeMatches(promo, dealerId)) continue;
            applyPromotionForDealer(tid, promo, chargeable, giftQtys, result);
        }
        giftQtys.forEach((giftProductId, qty) -> result.getLines().add(buildGiftLine(tid, giftProductId, qty)));
    }

    @SuppressWarnings("unchecked")
    private boolean dealerScopeMatches(Tuple promo, Long dealerId) {
        Object scope = promo.get("dealer_scope");
        if (scope == null) return true;
        Map<String, Object> map;
        if (scope instanceof Map<?, ?> m) map = (Map<String, Object>) m;
        else map = toMap(scope);
        if (map == null || map.isEmpty()) return true;
        Object ids = map.get("dealerIds");
        if (!(ids instanceof List<?> list) || list.isEmpty()) return true;
        return list.stream().map(this::toLong).anyMatch(id -> id != null && id.equals(dealerId));
    }

    private void applyPromotionForDealer(UUID tid, Tuple promo, List<V4Line> chargeable,
                                         Map<Long, BigDecimal> giftQtys, V4CalcResult result) {
        String type = str(promo.get("promo_type"), "").toUpperCase(Locale.ROOT);
        Long promotionId = toLong(promo.get("id"));
        String promoName = firstText(promo.get("name"), promo.get("code"), "促销活动");
        for (Tuple ruleT : pricing.promotionRules(promotionId)) {
            Map<String, Object> detail = toMap(ruleT.get("rule_detail"));
            String scope = str(detail.get("scope") != null ? detail.get("scope") : detail.get("targetType"), "SKU");
            Object pid = detail.get("targetProductId");
            Object cid = detail.get("targetProductLineId") != null ? detail.get("targetProductLineId") : detail.get("targetCategoryId");
            BigDecimal threshold = bd(firstNonNull(detail.get("thresholdQty"), detail.get("buyQty"), detail.get("threshold_qty")));
            List<V4Line> targets = scopeTargets(tid, chargeable, scope, pid, cid);
            if (targets.isEmpty()) continue;
            BigDecimal hitQty = targets.stream().map(V4Line::getQty).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (threshold.signum() > 0 && hitQty.compareTo(threshold) < 0) continue;
            switch (type) {
                case "QTY_DISCOUNT" -> applyQtyDiscount(tid, targets, detail, promotionId, promoName, result);
                case "QTY_REDUCE", "FULL_REDUCTION" -> applyQtyReduce(targets, detail, promotionId, type, promoName, result);
                case "GIFT", "MOQ" -> applyGift(tid, detail, promotionId, promoName, hitQty, giftQtys, result);
                default -> { }
            }
        }
    }

    private void applyQtyDiscount(UUID tid, List<V4Line> targets, Map<String, Object> detail,
                                  Long promotionId, String promoName, V4CalcResult result) {
        String discountType = str(detail.get("discountType"), "PERCENT").toUpperCase(Locale.ROOT);
        BigDecimal value = bd(firstNonNull(detail.get("discountValue"), detail.get("discountRate"), detail.get("discount")));
        if (value.signum() <= 0) return;
        BigDecimal totalBefore = money(targets.stream().map(this::lineAfterProductDiscount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal totalAfter;
        String desc;
        if ("FIXED_PRICE".equals(discountType)) {
            // 固定 EA 单价（含税）
            totalAfter = money(targets.stream().map(l -> value.multiply(l.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add));
            desc = "固定单价 ¥" + money(value).toPlainString();
        } else {
            BigDecimal rate = value.compareTo(BigDecimal.ONE) > 0 ? value.divide(V4Money.HUNDRED, 6, RoundingMode.HALF_UP) : value;
            totalAfter = money(totalBefore.multiply(BigDecimal.ONE.subtract(rate)));
            // value/rate 为「优惠比例」（0.10 = 优惠 10%）；中文折扣按「实付比例」展示（优惠 10% = 9 折）
            BigDecimal payableRate = BigDecimal.ONE.subtract(rate);
            desc = (payableRate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString()) + " 折";
        }
        BigDecimal discount = money(totalBefore.subtract(totalAfter));
        if (discount.signum() <= 0) return;
        distributePromoDiscount(targets, discount, "QTY_DISCOUNT", promotionId);
        for (V4Line l : targets) {
            setTax(l);
            if (l.getFinalAmount().signum() < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "第 [" + l.getLineLabel() + "]：促销打折后金额小于 0");
            }
        }
        String skuText = targets.stream().map(this::label).distinct().reduce((a, b) -> a + "、" + b).orElse("");
        result.getPromotionMessages().add(String.format("本单满足【%s】：%s 满 %s 件享%s，优惠 ¥%s",
                promoName, skuText, bd(firstNonNull(detail.get("thresholdQty"), detail.get("buyQty"))).stripTrailingZeros().toPlainString(),
                desc, discount.toPlainString()));
    }

    private void applyQtyReduce(List<V4Line> targets, Map<String, Object> detail, Long promotionId,
                                String type, String promoName, V4CalcResult result) {
        BigDecimal reduce = bd(firstNonNull(detail.get("reduceAmount"), detail.get("discountValue"), detail.get("reduction")));
        if (reduce.signum() <= 0) return;
        String cycle = str(detail.get("cycle"), "ONCE").toUpperCase(Locale.ROOT);
        BigDecimal threshold = bd(firstNonNull(detail.get("thresholdQty"), detail.get("buyQty")));
        BigDecimal times = BigDecimal.ONE;
        if ("EVERY_N".equals(cycle) && threshold.signum() > 0) {
            BigDecimal hitQty = targets.stream().map(V4Line::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            times = BigDecimal.ONE.add(hitQty.subtract(threshold).divide(threshold, 0, RoundingMode.FLOOR));
        }
        BigDecimal totalBefore = money(targets.stream().map(this::lineAfterProductDiscount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = money(reduce.multiply(times).min(totalBefore));
        if (discount.signum() <= 0) return;
        distributePromoDiscount(targets, discount, "QTY_REDUCE", promotionId);
        for (V4Line l : targets) { setTax(l); }
        String skuText = targets.stream().map(this::label).distinct().reduce((a, b) -> a + "、" + b).orElse("");
        result.getPromotionMessages().add(String.format("本单满足【%s】：%s 满减 ¥%s",
                promoName, skuText, discount.toPlainString()));
    }

    private void distributePromoDiscount(List<V4Line> targets, BigDecimal discount, String promoType, Long promotionId) {
        List<BigDecimal> weights = targets.stream().map(this::lineAfterProductDiscount).toList();
        BigDecimal[] shares = V4Money.allocate(discount.negate(), weights);
        for (int i = 0; i < targets.size(); i++) {
            V4Line l = targets.get(i);
            l.setPromoType(promoType);
            l.setPromotionId(promotionId);
            l.setPromoDiscountAmount(money(l.getPromoDiscountAmount().add(shares[i].abs())));
            l.setFinalAmount(money(lineAfterProductDiscount(l).subtract(l.getPromoDiscountAmount())));
        }
    }

    private void applyGift(UUID tid, Map<String, Object> detail, Long promotionId, String promoName,
                           BigDecimal hitQty, Map<Long, BigDecimal> giftQtys, V4CalcResult result) {
        Long giftProductId = toLong(detail.get("giftProductId"));
        BigDecimal giftQty = bd(detail.get("giftQty"));
        BigDecimal threshold = bd(firstNonNull(detail.get("thresholdQty"), detail.get("buyQty")));
        if (giftProductId == null || giftQty.signum() <= 0) return;
        if (pricing.isBom(tid, giftProductId)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "赠品不能是BOM母件");
        String cycle = str(detail.get("cycle"), "ONCE").toUpperCase(Locale.ROOT);
        BigDecimal times;
        if ("EVERY_N".equals(cycle)) {
            BigDecimal step = bd(firstNonNull(detail.get("everyN"), threshold));
            if (step.signum() <= 0) step = threshold;
            if (hitQty.compareTo(threshold) < 0) return;
            times = BigDecimal.ONE.add(hitQty.subtract(threshold).divide(step, 0, RoundingMode.FLOOR));
        } else {
            if (threshold.signum() > 0 && hitQty.compareTo(threshold) < 0) return;
            times = BigDecimal.ONE;
        }
        BigDecimal totalGift = giftQty.multiply(times);
        giftQtys.merge(giftProductId, totalGift, BigDecimal::add);
        Tuple gp = pricing.product(tid, giftProductId);
        String code = gp == null ? "" : str(gp.get("code"), "");
        String name = gp == null ? "" : firstText(gp.get("name_cn"), "");
        result.getPromotionMessages().add(String.format("本单满足【%s】：买满 %s 件，赠送 %s %s ×%s",
                promoName, threshold.stripTrailingZeros().toPlainString(), code, name, totalGift.stripTrailingZeros().toPlainString()));
    }

    private List<V4Line> scopeTargets(UUID tid, List<V4Line> lines, String scope, Object pid, Object cid) {
        Long productId = toLong(pid);
        Long categoryId = toLong(cid);
        if (productId == null && categoryId == null) return lines;
        Set<Long> descendants = categoryId == null ? Set.of() : pricing.productLineDescendants(tid, categoryId);
        List<V4Line> out = new ArrayList<>();
        for (V4Line l : lines) {
            if (l.isGift() || l.isLineZero() || "PARENT".equals(l.getLineLevel())) continue;
            if (productId != null && productId.equals(l.getProductId())) { out.add(l); continue; }
            if (categoryId != null) {
                Tuple p = pricing.product(tid, l.getProductId());
                Long pl = p == null ? null : toLong(p.get("product_line_id"));
                if (pl != null && descendants.contains(pl)) out.add(l);
            }
        }
        return out;
    }

    private void detectDuplicateSkuRows(List<V4Line> chargeable) {
        Map<Long, List<V4Line>> bySku = new LinkedHashMap<>();
        for (V4Line l : chargeable) {
            if (l.isGift() || l.getBomParentProductId() != null) continue;
            bySku.computeIfAbsent(l.getProductId(), k -> new ArrayList<>()).add(l);
        }
        for (Map.Entry<Long, List<V4Line>> e : bySku.entrySet()) {
            if (e.getValue().size() > 1) {
                V4Line first = e.getValue().get(0);
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "同一 SKU[" + first.getProductCode() + " " + first.getProductName()
                                + "] 存在多行，请合并为一行后提交。");
            }
        }
    }

    // ---- 代金券（订单层抵扣，不摊入单价） ----

    private void applyVoucher(UUID tid, String mode, Map<String, Object> params,
                              List<V4Line> chargeable, V4CalcResult result) {
        Long voucherId = toLong(params.get("voucherId"));
        if (voucherId == null) return;
        if (!mode.equals("VOUCHER")) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "代金券只能在代金券模式下使用；使用一口价、整单0或任何折扣时不能用券");
        }
        Tuple v = pricing.voucher(tid, voucherId);
        if (v == null) throw new BusinessException(ErrorCode.NOT_FOUND, "代金券不存在或已删除");
        String status = str(v.get("status"), "");
        if (!"ISSUED".equalsIgnoreCase(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "代金券当前状态[" + status + "]不可使用");
        }
        Long dealerId = toLong(params.get("dealerId"));
        Long vDealer = toLong(v.get("dealer_id"));
        if (vDealer != null && dealerId != null && !vDealer.equals(dealerId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该代金券不属于当前客户，不可使用");
        }
        Object vf = v.get("valid_from"), vt = v.get("valid_to");
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime validFrom = toOffsetDateTime(vf);
        OffsetDateTime validTo = toOffsetDateTime(vt);
        if (validFrom != null && now.isBefore(validFrom)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "代金券尚未到生效时间");
        if (validTo != null && now.isAfter(validTo)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "代金券已过期，不可使用");
        if (pricing.voucherInUse(tid, voucherId, toLong(params.get("orderId")))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该代金券已被使用，一单仅限一张");
        }
        BigDecimal face = money(bd(v.get("face_value")));
        BigDecimal minSpend = money(bd(v.get("min_spend")));
        BigDecimal original = orderTotal(chargeable);
        if (original.compareTo(minSpend) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "代金券最低消费 ¥" + minSpend.toPlainString() + "，当前商品合计 ¥" + original.toPlainString() + "，不满足使用条件");
        }
        if (!voucherScopeMatches(tid, v, chargeable)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "订单商品不在该代金券适用范围内");
        }
        if (face.compareTo(original) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "代金券面值 ¥" + face.toPlainString() + " 大于商品合计 ¥" + original.toPlainString() + "，不可使用");
        }
        result.setVoucherId(voucherId);
        result.setVoucherCode(str(v.get("code"), null));
        result.setVoucherAmount(face);
    }

    private boolean voucherScopeMatches(UUID tid, Tuple v, List<V4Line> chargeable) {
        String scopeType = str(v.get("scope_type"), "ALL").toUpperCase(Locale.ROOT);
        if ("ALL".equals(scopeType)) return true;
        Object refs = v.get("scope_refs");
        Set<Long> refIds = new HashSet<>();
        List<?> list = toList(refs);
        for (Object o : list) { Long id = toLong(o); if (id != null) refIds.add(id); }
        if (refIds.isEmpty()) return true;
        Set<Long> allowedSkus = new HashSet<>();
        if ("PRODUCT".equals(scopeType)) {
            allowedSkus.addAll(refIds);
        } else {
            for (Long catId : refIds) {
                for (V4Line l : chargeable) {
                    Tuple p = pricing.product(tid, l.getProductId());
                    Long pl = p == null ? null : toLong(p.get("product_line_id"));
                    if (pl != null && pricing.productLineDescendants(tid, catId).contains(pl)) allowedSkus.add(l.getProductId());
                }
            }
        }
        return chargeable.stream().allMatch(l -> allowedSkus.contains(l.getProductId()));
    }

    // ---- EA 单价（尾差落金额最大行） ----

    private void finalizeUnitPrices(List<V4Line> chargeable) {
        for (V4Line l : chargeable) {
            if (l.isGift() || l.getQty() == null || l.getQty().signum() <= 0) {
                l.setUnitPriceInclTax(BigDecimal.ZERO);
                continue;
            }
            BigDecimal per = l.getFinalAmount().divide(l.getQty(), 2, RoundingMode.HALF_UP);
            l.setUnitPriceInclTax(per);
        }
        // 尾差：各行 unitPrice*qty 之和与 finalAmount 的差，落金额最大行。
        V4Line maxLine = null;
        BigDecimal maxAmt = BigDecimal.ZERO;
        BigDecimal unitSum = BigDecimal.ZERO;
        for (V4Line l : chargeable) {
            if (l.getQty() == null || l.getQty().signum() <= 0) continue;
            unitSum = money(unitSum.add(money(l.getUnitPriceInclTax()).multiply(l.getQty())));
            if (l.getFinalAmount().compareTo(maxAmt) >= 0) { maxAmt = l.getFinalAmount(); maxLine = l; }
        }
        if (maxLine != null) {
            BigDecimal finalSum = orderTotal(chargeable);
            BigDecimal diff = money(finalSum.subtract(unitSum));
            if (diff.signum() != 0) {
                BigDecimal adjusted = money(money(maxLine.getUnitPriceInclTax()).add(diff.divide(maxLine.getQty(), 2, RoundingMode.HALF_UP)));
                maxLine.setUnitPriceInclTax(adjusted);
            }
        }
    }

    private void aggregate(V4CalcResult result, List<V4Line> lines, String mode) {
        List<V4Line> chargeable = lines.stream().filter(this::isChargeable).toList();
        BigDecimal original = BigDecimal.ZERO, prod = BigDecimal.ZERO, promo = BigDecimal.ZERO, line = BigDecimal.ZERO,
                dealer = BigDecimal.ZERO, header = BigDecimal.ZERO, finalAmt = BigDecimal.ZERO, excl = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (V4Line l : chargeable) {
            l.setDiscountAmount(money(l.getStandardAmount().subtract(l.getFinalAmount()).max(BigDecimal.ZERO)));
            original = money(original.add(l.getStandardAmount()));
            prod = money(prod.add(l.getProductDiscountAmount()));
            promo = money(promo.add(l.getPromoDiscountAmount()));
            line = money(line.add(l.getLineDiscountAmount().abs()));
            dealer = money(dealer.add(l.getDealerDiscountAmount()));
            header = money(header.add(l.getHeaderDiscountAmount().abs()));
            finalAmt = money(finalAmt.add(l.getFinalAmount()));
            setTax(l);
            excl = money(excl.add(l.getAmountExclTax()));
            tax = money(tax.add(l.getTaxAmount()));
        }
        result.setOriginalAmount(original);
        result.setProductDiscountTotal(prod);
        result.setPromoDiscountTotal(promo);
        result.setLineDiscountTotal(line);
        result.setDealerDiscountTotal(dealer);
        result.setHeaderDiscountTotal(header);
        result.setFinalAmount(finalAmt);
        result.setAmountExclTax(excl);
        result.setTaxAmount(tax);
        result.setPayableAmount(money(finalAmt.subtract(result.getVoucherAmount())));
    }

    private void validateNonNegative(List<V4Line> lines, String mode, BigDecimal voucherAmount) {
        for (V4Line l : lines) {
            if (!isChargeable(l)) continue;
            if (l.getFinalAmount() == null || l.getFinalAmount().signum() < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "第 [" + label(l) + "]：最终金额小于 0，请减少减额折扣");
            }
        }
        BigDecimal total = lines.stream().filter(this::isChargeable).map(V4Line::getFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (money(total).subtract(money(voucherAmount)).signum() < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "整单抵扣后金额小于 0，请减少券面值或减额折扣");
        }
    }

    // ---- 通用辅助 ----

    private boolean isChargeable(V4Line l) {
        return !l.isGift() && !l.isGroupHeader() && !"PARENT".equals(l.getLineLevel())
                && l.getStandardAmount() != null;
    }

    private BigDecimal lineWeight(V4Line l) {
        return l.isLineZero() ? BigDecimal.ZERO : money(l.getFinalAmount());
    }

    /** 产品全局折扣后、促销/行折扣前的金额（促销分摊权重）。 */
    private BigDecimal lineAfterProductDiscount(V4Line l) {
        if (l.isLineZero()) return BigDecimal.ZERO;
        return money(l.getStandardAmount().subtract(l.getProductDiscountAmount()));
    }

    private BigDecimal orderTotal(List<V4Line> chargeable) {
        return money(chargeable.stream().map(V4Line::getFinalAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void setTax(V4Line l) {
        Map<String, BigDecimal> split = V4Money.splitTax(l.getFinalAmount(), l.getTaxRate());
        l.setAmountExclTax(split.get("excl"));
        l.setTaxAmount(split.get("tax"));
    }

    private BigDecimal requirePositiveInt(Object raw, int lineNo) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + lineNo + " 行数量不能为空");
        }
        BigDecimal qty;
        try { qty = new BigDecimal(String.valueOf(raw).trim()); } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + lineNo + " 行数量必须为正整数");
        }
        if (qty.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + lineNo + " 行数量必须大于 0");
        if (qty.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + lineNo + " 行数量必须为正整数，不支持小数");
        }
        return qty;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<String, Object>> childDiscounts(Map<String, Object> row) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        Object raw = row.get("childDiscounts");
        if (!(raw instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Long pid = toLong(map.get("productId"));
            if (pid != null) out.put(pid, (Map<String, Object>) map);
        }
        return out;
    }

    private String label(V4Line l) {
        if (l.getLineLabel() != null) return l.getLineLabel();
        return (l.getProductCode() == null || l.getProductCode().isBlank() ? String.valueOf(l.getProductId()) : l.getProductCode())
                + " " + (l.getProductName() == null ? "" : l.getProductName());
    }

    private BigDecimal money(BigDecimal v) { return V4Money.money(v); }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

    private BigDecimal bd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(o).trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String str(Object o, String def) {
        if (o == null) return def;
        String t = String.valueOf(o).trim();
        return t.isEmpty() || "null".equals(t) ? def : t;
    }

    /** 原生 SQL 时间字段可能返回 OffsetDateTime/Instant/Timestamp，统一转换。 */
    private OffsetDateTime toOffsetDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime odt) return odt;
        if (o instanceof java.time.Instant inst) return inst.atOffset(java.time.ZoneOffset.UTC);
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (o instanceof java.util.Date dt) return dt.toInstant().atOffset(java.time.ZoneOffset.UTC);
        try { return OffsetDateTime.parse(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private String firstText(Object value, Object... fallbacks) {
        if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        for (Object f : fallbacks) if (f != null && !String.valueOf(f).isBlank()) return String.valueOf(f);
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object v : values) if (v != null && !String.valueOf(v).isBlank()) return v;
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (o instanceof CharSequence && !String.valueOf(o).isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(String.valueOf(o), Map.class);
                return parsed;
            } catch (Exception e) {
                return new HashMap<>();
            }
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<?> toList(Object o) {
        if (o instanceof List<?> l) return l;
        if (o == null) return List.of();
        try {
            String s = String.valueOf(o);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, List.class);
        } catch (Exception e) { return List.of(); }
    }
}
