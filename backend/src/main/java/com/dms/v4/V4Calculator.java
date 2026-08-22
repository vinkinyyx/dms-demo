package com.dms.v4;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class V4Calculator {
    private final V4PricingService pricing;

    public V4CalcResult expand(UUID tid, Long dealerId, List<Map<String, Object>> requestLines,
                               boolean applyPromotions, String headerDiscountType, BigDecimal headerDiscountValue) {
        List<V4Line> paid = new ArrayList<>();
        int seq = 1;
        for (Map<String, Object> row : requestLines) {
            Long pid = toLong(row.get("productId"));
            if (pid == null) continue;
            if (Boolean.TRUE.equals(row.get("isGift")) || "CHILD".equals(str(row.get("lineLevel"), null))) continue;
            BigDecimal qty = bd(row.get("qty"));
            if (qty.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + seq + " 行数量必须大于0");
            if (pricing.isBom(tid, pid)) {
                String group = "BOM-" + pid + "-" + seq;
                String version = str(row.get("bomVersion"), pricing.currentBomVersion(tid, pid));
                List<Map<String, Object>> comps = pricing.bomLines(tid, pid, version);
                Tuple parent = pricing.product(tid, pid);
                paid.add(buildParentLine(pid, parent, qty, version, group));
                Map<Long, Map<String, Object>> childDiscounts = childDiscounts(row);
                for (Map<String, Object> c : comps) {
                    Long childProductId = toLong(c.get("productId"));
                    if (pid.equals(childProductId)) continue;
                    V4Line l = buildLine(tid, dealerId, childProductId, qty.multiply(bd(c.get("quantity"))), c, pid, version, group);
                    Map<String, Object> discount = childDiscounts.get(childProductId);
                    if (discount != null) {
                        l.setLineDiscountType(str(discount.get("lineDiscountType"), null));
                        l.setLineDiscountValue(bd(discount.get("lineDiscountValue")));
                    }
                    paid.add(l);
                }
            } else {
                Tuple p = pricing.product(tid, pid);
                Map<String, Object> c = new HashMap<>();
                c.put("code", p == null ? null : p.get("code"));
                c.put("name", p == null ? null : firstText(p.get("name_cn"), p.get("code")));
                c.put("spec", p == null ? null : p.get("spec"));
                c.put("quantity", BigDecimal.ONE);
                V4Line l = buildLine(tid, dealerId, pid, qty, c, null, null, null);
                l.setLineDiscountType(str(row.get("lineDiscountType"), null));
                l.setLineDiscountValue(bd(row.get("lineDiscountValue")));
                paid.add(l);
            }
            seq++;
        }

        applyLineDiscounts(paid);
        List<V4Line> chargeable = paid.stream().filter(this::isChargeable).toList();
        BigDecimal lineAmountTotal = sum(chargeable, "afterLineDiscount");
        BigDecimal promoReduction = BigDecimal.ZERO;
        Map<Long, BigDecimal> giftQtys = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();

        if (applyPromotions) {
            for (Tuple promo : pricing.activePromotions(tid, dealerId)) {
                if (!promotionMatches(promo, dealerId, paid)) continue;
                String type = str(promo.get("promo_type"), "");
                String promoName = firstText(promo.get("name"), promo.get("code"), "促销规则").toString();
                for (Tuple ruleT : pricing.promotionRules(toLong(promo.get("id")))) {
                    Map detail = toMap(ruleT.get("rule_detail"));
                    if ("GIFT".equalsIgnoreCase(type) || "MOQ".equalsIgnoreCase(type)) {
                        applyGift(tid, paid, detail, giftQtys, promoName, messages);
                    } else if ("FULL_REDUCTION".equalsIgnoreCase(type)) {
                        promoReduction = promoReduction.add(applyReduction(tid, paid, detail, promoName, messages));
                    }
                }
            }
        }
        BigDecimal headerDiscount = V4Money.discount(lineAmountTotal, headerDiscountType, headerDiscountValue == null ? BigDecimal.ZERO : headerDiscountValue);
        BigDecimal totalOrderReduction = money(promoReduction.add(headerDiscount)).min(lineAmountTotal);
        allocateOrderReduction(chargeable, lineAmountTotal, totalOrderReduction, promoReduction, headerDiscount);

        List<V4Line> result = new ArrayList<>(paid);
        giftQtys.forEach((giftProductId, qty) -> result.add(buildGiftLine(tid, giftProductId, qty)));
        validate(result);
        return V4CalcResult.of(result, messages);
    }

    private V4Line buildParentLine(Long pid, Tuple parent, BigDecimal qty, String version, String group) {
        String parentCode = parent == null ? "" : str(parent.get("code"), "");
        String parentName = parent == null ? parentCode : firstText(parent.get("name_cn"), parentCode);
        return V4Line.builder()
                .productId(pid).productCode(parentCode).productName(parentName).productSpec(parent == null ? "" : str(parent.get("spec"), ""))
                .qty(qty).componentQty(BigDecimal.ONE).unitPriceExclTax(BigDecimal.ZERO).taxRate(BigDecimal.ZERO)
                .standardPriceInclTax(BigDecimal.ZERO).standardAmount(BigDecimal.ZERO)
                .lineDiscountType(null).lineDiscountValue(BigDecimal.ZERO).lineDiscountAmount(BigDecimal.ZERO)
                .promoDiscountAmount(BigDecimal.ZERO).headerDiscountAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO).amountExclTax(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO)
                .gift(false).bomVersion(version).bomGroupNo(group).lineLevel("PARENT").groupHeader(true).build();
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

    private void applyLineDiscounts(List<V4Line> lines) {
        for (V4Line l : lines) {
            BigDecimal discount = isChargeable(l) ? V4Money.discount(l.getStandardAmount(), l.getLineDiscountType(), l.getLineDiscountValue()) : BigDecimal.ZERO;
            l.setLineDiscountAmount(discount);
            l.setPromoDiscountAmount(BigDecimal.ZERO);
            l.setHeaderDiscountAmount(BigDecimal.ZERO);
            l.setDiscountAmount(discount);
            l.setFinalAmount(money(l.getStandardAmount().subtract(discount)));
            setTax(l, l.getFinalAmount());
        }
    }

    private boolean isChargeable(V4Line l) {
        return !l.isGift() && !l.isGroupHeader() && !"PARENT".equals(l.getLineLevel())
                && l.getStandardAmount() != null && l.getStandardAmount().signum() > 0;
    }

    private void allocateOrderReduction(List<V4Line> chargeable, BigDecimal lineAmountTotal, BigDecimal totalReduction,
                                        BigDecimal promoPortion, BigDecimal headerPortion) {
        if (chargeable.isEmpty() || lineAmountTotal.signum() <= 0 || totalReduction.signum() <= 0) return;
        BigDecimal denom = promoPortion.add(headerPortion);
        BigDecimal allocated = BigDecimal.ZERO;
        int maxIdx = 0;
        BigDecimal maxBase = BigDecimal.ZERO;
        BigDecimal[] shares = new BigDecimal[chargeable.size()];
        for (int i = 0; i < chargeable.size(); i++) {
            V4Line l = chargeable.get(i);
            BigDecimal base = money(l.getStandardAmount().subtract(l.getLineDiscountAmount()));
            BigDecimal share = totalReduction.multiply(base).divide(lineAmountTotal, 2, RoundingMode.HALF_UP).min(base);
            shares[i] = share;
            allocated = allocated.add(share);
            if (base.compareTo(maxBase) >= 0) { maxBase = base; maxIdx = i; }
        }
        BigDecimal diff = totalReduction.subtract(allocated);
        V4Line maxLine = chargeable.get(maxIdx);
        BigDecimal maxRemain = money(maxLine.getStandardAmount().subtract(maxLine.getLineDiscountAmount())).subtract(shares[maxIdx]);
        shares[maxIdx] = shares[maxIdx].add(diff).min(maxRemain.add(shares[maxIdx])).max(BigDecimal.ZERO);
        for (int i = 0; i < chargeable.size(); i++) {
            V4Line l = chargeable.get(i);
            BigDecimal share = shares[i];
            BigDecimal promoAmt = denom.signum() <= 0 ? BigDecimal.ZERO : share.multiply(promoPortion).divide(denom, 2, RoundingMode.HALF_UP);
            BigDecimal headerAmt = share.subtract(promoAmt).max(BigDecimal.ZERO);
            l.setPromoDiscountAmount(promoAmt);
            l.setHeaderDiscountAmount(headerAmt);
            l.setDiscountAmount(money(l.getLineDiscountAmount().add(promoAmt).add(headerAmt)));
            l.setFinalAmount(money(l.getStandardAmount().subtract(l.getDiscountAmount()).max(BigDecimal.ZERO)));
            setTax(l, l.getFinalAmount());
        }
    }

    private V4Line buildLine(UUID tid, Long dealerId, Long pid, BigDecimal qty, Map<String, Object> comp, Long bomParent, String bomVersion, String group) {
        V4PricingService.PriceUse use = bomParent == null ? V4PricingService.PriceUse.STANDALONE : V4PricingService.PriceUse.BOM_COMPONENT;
        V4PricingService.Price price = pricing.salesPrice(tid, pid, dealerId, use, bomParent);
        BigDecimal std = V4Money.money(price.incl().multiply(qty));
        V4Line l = V4Line.builder()
                .productId(pid).productCode(str(comp.get("code"), "")).productName(str(comp.get("name"), "")).productSpec(str(comp.get("spec"), ""))
                .qty(qty).componentQty(bd(comp.get("quantity"))).unitPriceExclTax(price.excl()).taxRate(price.rate()).standardPriceInclTax(price.incl())
                .standardAmount(std).lineDiscountType(null).lineDiscountValue(BigDecimal.ZERO).lineDiscountAmount(BigDecimal.ZERO)
                .promoDiscountAmount(BigDecimal.ZERO).headerDiscountAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .finalAmount(std).gift(false).bomParentProductId(bomParent).bomVersion(bomVersion).bomGroupNo(group).lineLevel(bomParent == null ? "NORMAL" : "CHILD")
                .batchNo(str(comp.get("batchNo"), null)).serialNo(str(comp.get("serialNo"), null)).build();
        setTax(l, std);
        return l;
    }

    private void setTax(V4Line l, BigDecimal amount) {
        var tax = V4Money.splitTax(amount.max(BigDecimal.ZERO), l.getTaxRate());
        l.setAmountExclTax(tax.get("excl"));
        l.setTaxAmount(tax.get("tax"));
    }

    @SuppressWarnings("unchecked")
    private boolean promotionMatches(Tuple promo, Long dealerId, List<V4Line> lines) {
        return scopeMatches(promo.get("dealer_scope"), dealerId) && productScopeMatches(promo.get("product_scope"), lines);
    }

    @SuppressWarnings("unchecked")
    private boolean scopeMatches(Object rawScope, Long id) {
        Map scope = toMap(rawScope);
        if (scope.isEmpty()) return true;
        Object type = scope.get("type");
        if ("ALL".equals(type) || Boolean.TRUE.equals(scope.get("all"))) return true;
        Object ids = scope.get("ids");
        if (!(ids instanceof List<?> list)) return false;
        return list.stream().map(String::valueOf).anyMatch(v -> v.equals(String.valueOf(id)));
    }

    @SuppressWarnings("unchecked")
    private boolean productScopeMatches(Object rawScope, List<V4Line> lines) {
        Map scope = toMap(rawScope);
        if (scope.isEmpty()) return true;
        Object type = scope.get("type");
        if ("ALL".equals(type) || Boolean.TRUE.equals(scope.get("all"))) return true;
        Object ids = scope.get("ids");
        if (!(ids instanceof List<?> list)) return false;
        Set<String> wanted = new HashSet<>();
        list.forEach(v -> wanted.add(String.valueOf(v)));
        return lines.stream().filter(l -> !l.isGift() && !"PARENT".equals(l.getLineLevel())).map(l -> String.valueOf(l.getProductId())).anyMatch(wanted::contains);
    }

    private void applyGift(UUID tid, List<V4Line> lines, Map detail, Map<Long, BigDecimal> giftQtys, String promoName, List<String> messages) {
        BigDecimal threshold = firstPositive(bd(detail.get("thresholdQty")), bd(detail.get("buyQty")), bd(detail.get("everyN")));
        Long giftProductId = toLong(firstNonNull(detail.get("giftProductId"), detail.get("gift_product_id")));
        BigDecimal giftQty = firstPositive(bd(detail.get("giftQty")), bd(detail.get("gift_qty")));
        String cycle = str(firstNonNull(detail.get("cycle"), detail.get("cyclic")), "ONCE");
        boolean cyclic = "EVERY_N".equalsIgnoreCase(cycle) || Boolean.TRUE.equals(detail.get("cyclic"));
        if (threshold.signum() <= 0 || giftProductId == null || giftQty.signum() <= 0) return;
        if (pricing.isBom(tid, giftProductId)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "赠品不能是BOM母件");
        BigDecimal hit = hitQty(tid, lines, detail.get("targetProductId"), detail.get("targetProductLineId"));
        BigDecimal times;
        if (cyclic) {
            // 每满N循环：达到起赠门槛 threshold(A) 后，每满 step 个再赠一次。step 取 everyN，缺省回退到 threshold。
            BigDecimal step = firstPositive(bd(detail.get("everyN")), threshold);
            if (hit.compareTo(threshold) < 0) times = BigDecimal.ZERO;
            else times = BigDecimal.ONE.add(hit.subtract(threshold).divide(step, 0, RoundingMode.FLOOR));
        } else {
            times = hit.compareTo(threshold) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (times.signum() <= 0) return;
        BigDecimal totalGift = giftQty.multiply(times);
        giftQtys.merge(giftProductId, totalGift, BigDecimal::add);
        Tuple gp = pricing.product(tid, giftProductId);
        String code = gp == null ? "" : str(gp.get("code"), "");
        String name = gp == null ? "" : firstText(gp.get("name_cn"), "").toString();
        messages.add(String.format("本单因满足【%s】，赠送 %s %s ×%s", promoName, code, name, totalGift.stripTrailingZeros().toPlainString()));
    }

    private V4Line buildGiftLine(UUID tid, Long giftProductId, BigDecimal qty) {
        Tuple p = pricing.product(tid, giftProductId);
        return V4Line.builder().productId(giftProductId).productCode(p==null?"":str(p.get("code"),"")).productName(p==null?"":str(p.get("name_cn"),"")).productSpec(p==null?"":str(p.get("spec"),""))
                .qty(qty).componentQty(BigDecimal.ONE).unitPriceExclTax(BigDecimal.ZERO).taxRate(BigDecimal.ZERO).standardPriceInclTax(BigDecimal.ZERO).standardAmount(BigDecimal.ZERO)
                .lineDiscountAmount(BigDecimal.ZERO).promoDiscountAmount(BigDecimal.ZERO).headerDiscountAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO).finalAmount(BigDecimal.ZERO)
                .amountExclTax(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO).gift(true).lineLevel("NORMAL").build();
    }

    private BigDecimal applyReduction(UUID tid, List<V4Line> lines, Map detail, String promoName, List<String> messages) {
        BigDecimal hit = hitQty(tid, lines, detail.get("targetProductId"), detail.get("targetProductLineId"));
        BigDecimal threshold = firstPositive(bd(detail.get("thresholdQty")), bd(detail.get("buyQty")), bd(detail.get("everyN")));
        if (threshold.signum() > 0 && hit.compareTo(threshold) < 0) return BigDecimal.ZERO;
        List<V4Line> targets = targetLines(tid, lines, detail.get("targetProductId"), detail.get("targetProductLineId"));
        if (targets.isEmpty()) return BigDecimal.ZERO;
        BigDecimal base = money(targets.stream().map(l -> l.getStandardAmount().subtract(l.getLineDiscountAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (base.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal value = firstPositive(bd(detail.get("reduceAmount")), bd(detail.get("discountValue")));
        if (value.signum() <= 0) return BigDecimal.ZERO;
        String mode = str(detail.get("discountType"), "AMOUNT");
        boolean rate = "RATE".equalsIgnoreCase(mode) || "PERCENT".equalsIgnoreCase(mode);
        String cycle = str(firstNonNull(detail.get("cycle"), detail.get("cyclic")), "ONCE");
        boolean cyclic = "EVERY_N".equalsIgnoreCase(cycle) || Boolean.TRUE.equals(detail.get("cyclic"));
        BigDecimal times;
        if (cyclic) {
            BigDecimal step = firstPositive(bd(detail.get("everyN")), threshold);
            if (hit.compareTo(threshold) < 0) times = BigDecimal.ZERO;
            else times = BigDecimal.ONE.add(hit.subtract(threshold).divide(step, 0, RoundingMode.FLOOR));
        } else {
            times = hit.compareTo(threshold) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (times.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal once = rate ? base.multiply(value.min(BigDecimal.ONE)) : value;
        BigDecimal discount = money(once.multiply(times).min(base));
        if (discount.signum() <= 0) return BigDecimal.ZERO;
        String cycleText = cyclic ? "（每满" + firstPositive(bd(detail.get("everyN")), threshold).stripTrailingZeros().toPlainString() + "循环）" : "";
        messages.add(String.format("本单因满足【%s】%s，整单减免 ¥%s", promoName, cycleText, discount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        return discount;
    }

    private BigDecimal hitQty(UUID tid, List<V4Line> lines, Object pid, Object lineId) {
        return targetLines(tid, lines, pid, lineId).stream().map(V4Line::getQty).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<V4Line> targetLines(UUID tid, List<V4Line> lines, Object pid, Object lineId) {
        Long productId = toLong(pid);
        Long productLineId = toLong(lineId);
        if (productId == null && productLineId == null) return lines.stream().filter(l -> !l.isGift() && !"PARENT".equals(l.getLineLevel())).toList();
        Set<Long> descendants = productLineId == null ? Set.of() : pricing.productLineDescendants(tid, productLineId);
        return lines.stream().filter(l -> {
            if (l.isGift() || "PARENT".equals(l.getLineLevel())) return false;
            if (productId != null && productId.equals(l.getProductId())) return true;
            if (productLineId != null) {
                Tuple p = pricing.product(tid, l.getProductId());
                Long pl = p == null ? null : toLong(p.get("product_line_id"));
                return pl != null && descendants.contains(pl);
            }
            return false;
        }).toList();
    }

    private void validate(List<V4Line> lines) {
        for (int i = 0; i < lines.size(); i++) {
            V4Line l = lines.get(i);
            if (l.getFinalAmount() == null || l.getFinalAmount().signum() < 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "第 " + (i+1) + " 行折扣后金额不能小于0");
        }
    }

    private BigDecimal sum(List<V4Line> lines, String field) {
        return V4Money.money(lines.stream().map(l -> switch (field) {
            case "lineDiscount" -> l.getLineDiscountAmount();
            case "afterLineDiscount" -> l.getStandardAmount().subtract(l.getLineDiscountAmount());
            default -> l.getFinalAmount();
        }).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal money(BigDecimal v) { return V4Money.money(v); }
    private Map toMap(Object o) {
        if (o instanceof Map m) return m;
        if (o == null) return Map.of();
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(String.valueOf(o), Map.class); } catch (Exception e) { return Map.of(); }
    }
    private Long toLong(Object o){ return o==null?null:Long.valueOf(String.valueOf(o)); }
    private BigDecimal bd(Object o){ return o==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(o)); }
    private String str(Object o,String def){ return o==null?def:String.valueOf(o); }
    private Object firstNonNull(Object... values) { for (Object v : values) if (v != null && !String.valueOf(v).isBlank()) return v; return null; }
    private BigDecimal firstPositive(BigDecimal... values) { for (BigDecimal v : values) if (v != null && v.signum() > 0) return v; return BigDecimal.ZERO; }
    private String firstText(Object value, Object... fallbacks) {
        if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        for (Object f : fallbacks) if (f != null && !String.valueOf(f).isBlank()) return String.valueOf(f);
        return "";
    }
}
