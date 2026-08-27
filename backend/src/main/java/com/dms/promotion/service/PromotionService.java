package com.dms.promotion.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.promotion.entity.Promotion;
import com.dms.promotion.entity.PromotionRule;
import com.dms.promotion.repository.PromotionRepository;
import com.dms.promotion.repository.PromotionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {
    private static final Set<String> ALLOWED_TYPES = Set.of("GIFT", "FULL_REDUCTION", "QTY_DISCOUNT", "QTY_REDUCE");
    private final PromotionRepository repository;
    private final PromotionRuleRepository ruleRepository;
    private final com.dms.v4.V4PricingService pricingService;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<Promotion> list(PageQuery pageQuery, String code, String name, String promoType, String status) {
        UUID tenantId = TenantContext.getTenantId();
        int pageNumber = Math.max(0, pageQuery.getPage() == null ? 0 : pageQuery.getPage() - 1);
        int pageSize = Math.min(1000, Math.max(1, pageQuery.getSize() == null ? 20 : pageQuery.getSize()));
        StringBuilder where = new StringBuilder("WHERE p.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        int idx = 1;
        if (tenantId != null) {
            where.append(" AND p.tenant_id = ?").append(idx++);
            params.add(tenantId);
        }
        if (code != null && !code.isBlank()) { where.append(" AND p.code ILIKE ?").append(idx++); params.add("%" + code.trim() + "%"); }
        if (name != null && !name.isBlank()) { where.append(" AND p.name ILIKE ?").append(idx++); params.add("%" + name.trim() + "%"); }
        if (promoType != null && !promoType.isBlank()) { where.append(" AND p.promo_type = ?").append(idx++); params.add(promoType); }
        if (status != null && !status.isBlank()) { where.append(" AND p.status = ?").append(idx++); params.add(status); }

        var cnt = em.createNativeQuery("SELECT COUNT(*) FROM promotions p " + where);
        for (int i = 0; i < params.size(); i++) cnt.setParameter(i + 1, params.get(i));
        long total = ((Number) cnt.getSingleResult()).longValue();

        String sortExpr = buildSortExpr(pageQuery.getSort(), "p.");
        var q = em.createNativeQuery("SELECT p.* FROM promotions p " + where + " ORDER BY " + sortExpr + " LIMIT ?" + idx + " OFFSET ?" + (idx + 1), Promotion.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, pageSize);
        q.setParameter(idx + 1, pageNumber * pageSize);
        @SuppressWarnings("unchecked")
        List<Promotion> rows = q.getResultList();
        return new PageResult<>(total, pageNumber + 1, pageSize, rows);
    }

    private String buildSortExpr(String sort, String prefix) {
        String defaultSort = prefix + "updated_at DESC, " + prefix + "id DESC";
        if (sort == null || sort.isBlank()) return defaultSort;
        List<String> orders = new ArrayList<>();
        for (String seg : sort.split(";")) {
            String[] parts = seg.split(",");
            if (parts.length == 0 || parts[0].isBlank()) continue;
            String field = parts[0].trim();
            String dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc") ? "ASC" : "DESC";
            switch (field) {
                case "id" -> orders.add(prefix + "id " + dir);
                case "code" -> orders.add(prefix + "code " + dir);
                case "name" -> orders.add(prefix + "name " + dir);
                case "promoType" -> orders.add(prefix + "promo_type " + dir);
                case "status" -> orders.add(prefix + "status " + dir);
                case "priority" -> orders.add(prefix + "priority " + dir);
                case "validFrom" -> orders.add(prefix + "valid_from " + dir);
                case "validTo" -> orders.add(prefix + "valid_to " + dir);
                case "createdAt" -> orders.add(prefix + "created_at " + dir);
                case "updatedAt" -> orders.add(prefix + "updated_at " + dir);
                default -> {}
            }
        }
        return orders.isEmpty() ? defaultSort : String.join(",", orders);
    }

    @Transactional(readOnly = true)
    public Promotion get(Long id) {
        Promotion p = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "促销规则不存在"));
        p.setApplicableProducts(resolveScope(p.getProductScope(), "products", "name_cn"));
        p.setApplicableDealers(resolveScope(p.getDealerScope(), "dealers", "name"));
        List<PromotionRule> rules = ruleRepository.findByPromotionIdOrderBySeqAsc(id);
        rules.forEach(this::fillRuleDisplayNames);
        p.setRules(rules);
        return p;
    }

    @Transactional
    public Promotion create(Promotion req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        validate(req);
        req.setId(null);
        req.ensureMaps();
        req.setTenantId(tenantId);
        if (req.getStatus() == null) req.setStatus("draft");
        if (req.getPriority() == null) req.setPriority(50);
        if (req.getExclusive() == null) req.setExclusive(false);
        req.setCreatedBy(TenantContext.getUserId());
        req.setUpdatedAt(OffsetDateTime.now());
        req.ensureMaps();
        checkSkuUniqueness(req, null);
        Promotion saved = repository.save(req);
        replaceRules(saved, req.getRules());
        List<PromotionRule> rules = ruleRepository.findByPromotionIdOrderBySeqAsc(saved.getId());
        rules.forEach(this::fillRuleDisplayNames);
        saved.setRules(rules);
        return saved;
    }

    @Transactional
    public Promotion update(Long id, Promotion patch) {
        Promotion old = get(id);
        validate(patch);
        old.setName(patch.getName());
        old.setPromoType(patch.getPromoType());
        old.setPriority(patch.getPriority() == null ? 50 : patch.getPriority());
        old.setValidFrom(patch.getValidFrom());
        old.setValidTo(patch.getValidTo());
        old.setDealerScope(patch.getDealerScope());
        old.setProductScope(patch.getProductScope());
        old.setExclusive(patch.getExclusive() == null ? false : patch.getExclusive());
        old.setDescription(patch.getDescription());
        old.setStatus(patch.getStatus() == null ? "draft" : patch.getStatus());
        old.setUpdatedAt(OffsetDateTime.now());
        old.setUpdatedBy(TenantContext.getUserId());
        checkSkuUniqueness(old, old.getId());
        Promotion saved = repository.save(old);
        if (patch.getRules() != null) replaceRules(saved, patch.getRules());
        List<PromotionRule> rules = ruleRepository.findByPromotionIdOrderBySeqAsc(saved.getId());
        rules.forEach(this::fillRuleDisplayNames);
        saved.setRules(rules);
        return saved;
    }

    @Transactional
    public void deactivate(Long id) {
        Promotion p = get(id);
        p.setStatus("inactive");
        p.setUpdatedAt(OffsetDateTime.now());
        p.setUpdatedBy(TenantContext.getUserId());
        repository.save(p);
    }

    @Transactional
    public void activate(Long id) {
        Promotion p = get(id);
        checkSkuUniqueness(p, p.getId());
        p.setStatus("active");
        p.setUpdatedAt(OffsetDateTime.now());
        p.setUpdatedBy(TenantContext.getUserId());
        repository.save(p);
    }

    /**
     * 同一 SKU 在同一有效时间段只能命中一种促销：把 SKU/品类展开为 SKU 集合，
     * 与其它有效促销规则校验时间窗交集；冲突则拦截并列出活动名称与时段。
     */
    private void checkSkuUniqueness(Promotion req, Long excludePromotionId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return;
        java.time.OffsetDateTime from = req.getValidFrom();
        java.time.OffsetDateTime to = req.getValidTo();
        Set<Long> mySkus = new HashSet<>();
        for (PromotionRule r : (req.getRules() == null ? List.<PromotionRule>of() : req.getRules())) {
            Map<String,Object> detail = r.getRuleDetail() == null ? Map.of() : r.getRuleDetail();
            String scope = str(detail.get("scope") != null ? detail.get("scope") : detail.get("targetType"), "SKU");
            Object pid = detail.get("targetProductId");
            Object cid = detail.get("targetProductLineId") != null ? detail.get("targetProductLineId") : detail.get("targetCategoryId");
            if ("LINE".equalsIgnoreCase(scope) || "CATEGORY".equalsIgnoreCase(scope)) {
                mySkus.addAll(pricingService.expandScopeToSkus(tenantId, null, cid));
            } else {
                mySkus.addAll(pricingService.expandScopeToSkus(tenantId, pid, null));
            }
        }
        if (mySkus.isEmpty()) return;
        List<String> conflicts = new ArrayList<>();
        for (var t : pricingService.allPromotionRulesForValidation(tenantId)) {
            Long otherId = toLong(t.get("promotion_id"));
            if (excludePromotionId != null && excludePromotionId.equals(otherId)) continue;
            String status = str(t.get("status"), "");
            if ("inactive".equals(status) || "void".equals(status) || "disabled".equals(status)) continue;
            Object otherFrom = t.get("valid_from");
            Object otherTo = t.get("valid_to");
            if (!timeOverlap(from, to,
                    otherFrom == null ? null : java.time.OffsetDateTime.parse(String.valueOf(otherFrom)),
                    otherTo == null ? null : java.time.OffsetDateTime.parse(String.valueOf(otherTo)))) continue;
            @SuppressWarnings("unchecked")
            Map<String,Object> detail = t.get("rule_detail") instanceof Map ? (Map<String,Object>) t.get("rule_detail") : Map.of();
            String scope = str(detail.get("scope") != null ? detail.get("scope") : detail.get("targetType"), "SKU");
            Object pid = detail.get("targetProductId");
            Object cid = detail.get("targetProductLineId") != null ? detail.get("targetProductLineId") : detail.get("targetCategoryId");
            Set<Long> otherSkus = ("LINE".equalsIgnoreCase(scope) || "CATEGORY".equalsIgnoreCase(scope))
                    ? pricingService.expandScopeToSkus(tenantId, null, cid)
                    : pricingService.expandScopeToSkus(tenantId, pid, null);
            for (Long s : mySkus) {
                if (otherSkus.contains(s)) {
                    conflicts.add("SKU[" + skuLabel(tenantId, s) + "] 在 " + fmt(otherFrom) + "~" + fmt(otherTo)
                            + " 已存在促销【" + str(t.get("promo_name"), String.valueOf(otherId)) + "】，不可重复配置。");
                    break;
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "同一 SKU 在同一时间段只能存在一种促销：" + String.join("；", conflicts));
        }
    }

    private boolean timeOverlap(java.time.OffsetDateTime a1, java.time.OffsetDateTime a2,
                                java.time.OffsetDateTime b1, java.time.OffsetDateTime b2) {
        long lo = a1 == null ? Long.MIN_VALUE : a1.toEpochSecond();
        long hi = a2 == null ? Long.MAX_VALUE : a2.toEpochSecond();
        long lo2 = b1 == null ? Long.MIN_VALUE : b1.toEpochSecond();
        long hi2 = b2 == null ? Long.MAX_VALUE : b2.toEpochSecond();
        return lo <= hi2 && lo2 <= hi;
    }

    private String fmt(Object o) { return o == null ? "不限" : String.valueOf(o).replace("T", " ").substring(0, Math.min(16, String.valueOf(o).replace("T"," ").length())); }

    private String skuLabel(UUID tenantId, Long productId) {
        var rows = em.createNativeQuery("SELECT code, name_cn FROM products WHERE id=?1").setParameter(1, productId).getResultList();
        if (rows.isEmpty()) return String.valueOf(productId);
        Object[] row = (Object[]) rows.get(0);
        return row[0] + " " + (row[1] == null ? "" : row[1]);
    }

    private void validate(Promotion req) {
        if (req.getPromoType() == null || !ALLOWED_TYPES.contains(req.getPromoType())) throw new BusinessException(ErrorCode.PARAM_INVALID, "促销类型只能是 GIFT、FULL_REDUCTION、QTY_DISCOUNT 或 QTY_REDUCE");
        if (req.getName() == null || req.getName().isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "促销名称不能为空");
        if (req.getValidFrom() != null && req.getValidTo() != null && req.getValidTo().isBefore(req.getValidFrom())) throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间不能早于开始时间");
    }

    private void replaceRules(Promotion promotion, List<PromotionRule> requested) {
        List<PromotionRule> existing = ruleRepository.findByPromotionIdOrderBySeqAsc(promotion.getId());
        ruleRepository.deleteAll(existing);
        if (requested == null) return;
        int seq = 1;
        for (PromotionRule r : requested) {
            Map<String,Object> detail = r.getRuleDetail() == null ? new HashMap<>() : new HashMap<>(r.getRuleDetail());
            normalizeNumbers(detail, "thresholdQty", "giftQty", "everyN", "reduceAmount", "discountValue", "discountRate", "discount");
            String targetType = str(detail.get("targetType"), "SKU");
            if (!"SKU".equalsIgnoreCase(targetType) && !"LINE".equalsIgnoreCase(targetType))
                throw new BusinessException(ErrorCode.PARAM_INVALID, "命中对象类型只能是 SKU 或 产品层次");
            Object targetProductId = "LINE".equalsIgnoreCase(targetType) ? null : detail.get("targetProductId");
            Object targetProductLineId = "LINE".equalsIgnoreCase(targetType) ? detail.get("targetProductLineId") : null;
            if (targetProductId == null && targetProductLineId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "请选择目标SKU或产品层次");
            if (promotion.getPromoType().equals("GIFT")) {
                Long giftProductId = toLong(detail.get("giftProductId"));
                if (giftProductId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "请选择赠品");
                detail.put("giftProductId", giftProductId);
                if (isBom(giftProductId)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "赠品不能是BOM母件");
                String cycle = str(detail.get("cycle"), "ONCE");
                if (!"ONCE".equalsIgnoreCase(cycle) && !"EVERY_N".equalsIgnoreCase(cycle))
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "赠送周期只能是 仅赠一次 或 每满N循环");
                detail.put("cycle", cycle.toUpperCase());
                if ("EVERY_N".equalsIgnoreCase(cycle)) {
                    BigDecimal everyN = toBd(detail.get("everyN"));
                    if (everyN.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写每满N数量");
                }
            } else if ("QTY_DISCOUNT".equals(promotion.getPromoType())) {
                BigDecimal thresholdQty = toBd(detail.get("thresholdQty"));
                if (thresholdQty.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写满N件门槛数量");
                String discountType = str(detail.get("discountType"), "PERCENT").toUpperCase();
                if (!"PERCENT".equals(discountType) && !"FIXED_PRICE".equals(discountType))
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "满件打折方式只能是 百分比 或 固定单价");
                detail.put("discountType", discountType);
                BigDecimal value = toBd(firstNonNull(detail.get("discountValue"), detail.get("discountRate"), detail.get("discount")));
                if (value.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写打折比例或固定单价");
                detail.put("targetType", targetType.toUpperCase());
            } else if ("QTY_REDUCE".equals(promotion.getPromoType())) {
                BigDecimal thresholdQty = toBd(detail.get("thresholdQty"));
                if (thresholdQty.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写满N件门槛数量");
                BigDecimal reduceAmount = toBd(detail.get("reduceAmount"));
                if (reduceAmount.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写满减金额");
                String cycle = str(detail.get("cycle"), "ONCE");
                if (!"ONCE".equalsIgnoreCase(cycle) && !"EVERY_N".equalsIgnoreCase(cycle))
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "减免周期只能是 仅一次 或 每满N循环");
                detail.put("cycle", cycle.toUpperCase());
                if ("EVERY_N".equalsIgnoreCase(cycle) && toBd(detail.get("everyN")).signum() <= 0)
                    throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写每满N数量");
                detail.put("targetType", targetType.toUpperCase());
            } else {
                 BigDecimal reduceAmount = toBd(detail.get("reduceAmount"));
                 BigDecimal discountValue = toBd(detail.get("discountValue"));
                 if (reduceAmount.signum() <= 0 && discountValue.signum() <= 0) {
                     throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写满减优惠金额");
                 }
                 String cycle = str(detail.get("cycle"), "ONCE");
                 if (!"ONCE".equalsIgnoreCase(cycle) && !"EVERY_N".equalsIgnoreCase(cycle))
                     throw new BusinessException(ErrorCode.PARAM_INVALID, "减免周期只能是 仅一次 或 每满N循环");
                 detail.put("cycle", cycle.toUpperCase());
                 if ("EVERY_N".equalsIgnoreCase(cycle)) {
                     BigDecimal everyN = toBd(detail.get("everyN"));
                     if (everyN.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_MISSING, "请填写每满N数量");
                 }
            }
            PromotionRule saved = PromotionRule.builder().promotionId(promotion.getId()).seq(seq++).ruleDetail(detail).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
            ruleRepository.save(saved);
        }
    }

    private void fillRuleDisplayNames(PromotionRule rule) {
        if (rule == null || rule.getRuleDetail() == null) return;
        Map<String, Object> detail = rule.getRuleDetail();
        Object productId = detail.get("targetProductId");
        Object lineId = detail.get("targetProductLineId");
        Object giftId = detail.get("giftProductId");
        if (productId != null) {
            Long pid = toLong(productId);
            if (pid == null) return;
            var rows = em.createNativeQuery("SELECT code, name_cn FROM products WHERE id=?1").setParameter(1, pid).getResultList();
            if (!rows.isEmpty()) {
                Object[] row = (Object[]) rows.get(0);
                detail.put("targetProductCode", row[0]);
                detail.put("targetProductName", row[1]);
            }
        }
        if (lineId != null) {
            Long lid = toLong(lineId);
            if (lid == null) return;
            var rows = em.createNativeQuery("SELECT code, name FROM product_lines WHERE id=?1").setParameter(1, lid).getResultList();
            if (!rows.isEmpty()) {
                Object[] row = (Object[]) rows.get(0);
                detail.put("targetProductLineCode", row[0]);
                detail.put("targetProductLineName", row[1]);
            }
        }
        if (giftId != null) {
            Long gid = toLong(giftId);
            if (gid == null) return;
            var rows = em.createNativeQuery("SELECT code, name_cn FROM products WHERE id=?1").setParameter(1, gid).getResultList();
            if (!rows.isEmpty()) {
                Object[] row = (Object[]) rows.get(0);
                detail.put("giftProductCode", row[0]);
                detail.put("giftProductName", row[1]);
            }
        }
        rule.setRuleDetail(detail);
    }

    private boolean isBom(Object productId) {
        Long pid = toLong(productId);
        if (pid == null) return false;
        var rows = em.createNativeQuery("SELECT 1 FROM product_bundles WHERE product_id=?1 AND version_status='active' AND deleted_at IS NULL LIMIT 1").setParameter(1, pid).getResultList();
        return !rows.isEmpty();
    }

    private void normalizeNumbers(Map<String, Object> detail, String... keys) {
        for (String key : keys) {
            Object value = detail.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                detail.remove(key);
                continue;
            }
            try {
                detail.put(key, new java.math.BigDecimal(String.valueOf(value)));
            } catch (NumberFormatException e) {
                detail.remove(key);
            }
        }
    }

    private Object firstNonNull(Object... values) { for (Object v : values) if (v != null && !String.valueOf(v).isBlank()) return v; return null; }
    private BigDecimal toBd(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(value).trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String str(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try { return Long.valueOf(text); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveScope(Map<String, Object> scope, String table, String nameCol) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (scope == null || scope.isEmpty()) return out;
        Object type = scope.get("type");
        Object ids = scope.get("ids");
        if ("ALL".equals(type) || scope.get("all") == Boolean.TRUE) {
            var rows = em.createNativeQuery("SELECT id, " + nameCol + " AS name FROM " + table + " LIMIT 500", jakarta.persistence.Tuple.class).getResultList();
            for (var t : (List<jakarta.persistence.Tuple>) rows) { Map<String,Object> m=new HashMap<>(); m.put("id",t.get("id")); m.put("name",t.get("name")); out.add(m); }
            return out;
        }
        List<Object> idList = new ArrayList<>();
        if (ids instanceof List) idList.addAll((List<Object>) ids);
        if (idList.isEmpty()) return out;
        var rows = em.createNativeQuery("SELECT id, " + nameCol + " AS name FROM " + table + " WHERE id IN (" + idList.stream().map(String::valueOf).reduce((a,b)->a+","+b).orElse("0") + ")", jakarta.persistence.Tuple.class).getResultList();
        for (var t : (List<jakarta.persistence.Tuple>) rows) { Map<String,Object> m=new HashMap<>(); m.put("id",t.get("id")); m.put("name",t.get("name")); out.add(m); }
        return out;
    }
}



