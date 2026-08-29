package com.dms.v4;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class V4PricingService {
    private final EntityManager em;

    public record Price(BigDecimal excl, BigDecimal rate, BigDecimal incl) {}

    /** v4.3.0 取价：带价格来源 CONTRACT/DEALER/GLOBAL，合同价优先。 */
    public record SourcedPrice(Price price, String source) {}

    public Price salesPrice(UUID tenantId, Long productId, Long dealerId) {
        return salesPrice(tenantId, productId, dealerId, PriceUse.STANDALONE, null);
    }

    public Price salesPrice(UUID tenantId, Long productId, Long dealerId, PriceUse use, Long bomParentProductId) {
        if (use == PriceUse.BOM_COMPONENT) {
            // v4.4.5：BOM 组件优先取 BOM 专属组件价（经销商价 > 全局价）；
            // 未维护组件价时回退到该组件的单品销售价（与 STANDALONE 一致：合同价 > 经销商价 > 全局价），
            // 仍取不到价格时抛业务异常，禁止静默按 0 计价（否则整套 BOM 免费）。
            Price p = findPrice(tenantId, productId, "DEALER", dealerId, "BOM_COMPONENT", bomParentProductId);
            if (p == null) p = findPrice(tenantId, productId, "GLOBAL", 0L, "BOM_COMPONENT", bomParentProductId);
            if (p == null) p = findPrice(tenantId, productId, "GLOBAL", null, "BOM_COMPONENT", bomParentProductId);
            if (p != null) return p;
            p = findPrice(tenantId, productId, "DEALER", dealerId, "STANDALONE", null);
            if (p == null) p = findPrice(tenantId, productId, "GLOBAL", 0L, "STANDALONE", null);
            if (p == null) p = findPrice(tenantId, productId, "GLOBAL", null, "STANDALONE", null);
            if (p != null) return p;
            String label = productLabel(tenantId, productId);
            String parentLabel = bomParentProductId == null ? "" : ("（BOM 组合 " + productLabel(tenantId, bomParentProductId) + "）");
            throw new com.dms.common.BusinessException(com.dms.common.ErrorCode.BUSINESS_RULE_VIOLATION,
                "产品 [" + label + "]" + parentLabel + " 没有维护有效销售价格（BOM 组件价或单品价），请先在「产品价格」中维护");
        }
        Price p = findPrice(tenantId, productId, "DEALER", dealerId, "STANDALONE", null);
        if (p == null) p = findPrice(tenantId, productId, "GLOBAL", 0L, "STANDALONE", null);
        if (p == null) p = findPrice(tenantId, productId, "GLOBAL", null, "STANDALONE", null);
        if (p == null) {
            String label = productLabel(tenantId, productId);
            throw new com.dms.common.BusinessException(com.dms.common.ErrorCode.BUSINESS_RULE_VIOLATION,
                "产品 [" + label + "] 没有维护有效单品销售价格，请先在「产品价格」中维护经销商价或全局价");
        }
        return p;
    }

    /**
     * v4.3.0 基础含税单价：合同价 > 客户基础价(DEALER) > 全局价(GLOBAL)。
     */
    public SourcedPrice basePrice(UUID tid, Long productId, Long dealerId) {
        Tuple cp = findContractPrice(tid, productId, dealerId);
        if (cp != null) {
            BigDecimal rate = bd(cp.get("tax_rate"));
            BigDecimal incl = bd(cp.get("price_incl_tax"));
            BigDecimal excl = bd(cp.get("price_excl_tax"));
            if (incl.signum() == 0 && excl.signum() > 0) incl = excl.multiply(BigDecimal.ONE.add(rate));
            if (excl.signum() == 0 && incl.signum() > 0) excl = incl.divide(BigDecimal.ONE.add(rate), 4, RoundingMode.HALF_UP);
            if (incl.signum() > 0) return new SourcedPrice(new Price(excl, rate, incl), "CONTRACT");
        }
        Price dealer = findPrice(tid, productId, "DEALER", dealerId, "STANDALONE", null);
        if (dealer != null && dealer.incl().signum() > 0) return new SourcedPrice(dealer, "DEALER");
        Price global = findPrice(tid, productId, "GLOBAL", 0L, "STANDALONE", null);
        if (global == null) global = findPrice(tid, productId, "GLOBAL", null, "STANDALONE", null);
        if (global != null && global.incl().signum() > 0) return new SourcedPrice(global, "GLOBAL");
        String label = productLabel(tid, productId);
        throw new com.dms.common.BusinessException(com.dms.common.ErrorCode.BUSINESS_RULE_VIOLATION,
                "产品 [" + label + "] 没有维护有效销售价格（合同价/经销商价/全局价），请先维护价格");
    }

    private Tuple findContractPrice(UUID tid, Long productId, Long dealerId) {
        String sql = "SELECT cp.price_incl_tax, cp.price_excl_tax, cp.tax_rate FROM contract_prices cp " +
                "JOIN contracts c ON c.id = cp.contract_id " +
                "WHERE cp.tenant_id=?1 AND cp.product_id=?2 AND cp.deleted_at IS NULL AND cp.status='active' " +
                "AND c.deleted_at IS NULL AND c.status='effective' " +
                "AND (cp.dealer_id IS NULL OR cp.dealer_id=?3) " +
                "AND (cp.valid_from IS NULL OR cp.valid_from <= CURRENT_DATE) " +
                "AND (cp.valid_to IS NULL OR cp.valid_to >= CURRENT_DATE) " +
                "AND (c.valid_from IS NULL OR c.valid_from <= CURRENT_DATE) " +
                "AND (c.valid_to IS NULL OR c.valid_to >= CURRENT_DATE) " +
                "ORDER BY (cp.dealer_id IS NOT NULL) DESC, cp.updated_at DESC LIMIT 1";
        var rs = em.createNativeQuery(sql, Tuple.class).setParameter(1, tid).setParameter(2, productId).setParameter(3, dealerId).getResultList();
        return rs.isEmpty() ? null : (Tuple) rs.get(0);
    }

    /** 产品全局折扣率（0~1，只减），当前日期生效；无则 0。 */
    public BigDecimal productGlobalDiscountRate(UUID tid, Long productId) {
        String sql = "SELECT discount_rate FROM product_global_discounts " +
                "WHERE tenant_id=?1 AND product_id=?2 AND deleted_at IS NULL AND status='active' " +
                "AND (valid_from IS NULL OR valid_from <= CURRENT_DATE) " +
                "AND (valid_to IS NULL OR valid_to >= CURRENT_DATE) " +
                "ORDER BY updated_at DESC LIMIT 1";
        var rs = em.createNativeQuery(sql, Tuple.class).setParameter(1, tid).setParameter(2, productId).getResultList();
        if (rs.isEmpty()) return BigDecimal.ZERO;
        return bd(((Tuple) rs.get(0)).get("discount_rate"));
    }

    /** 客户全局折扣率（0~1，只减），当前日期生效；无则 0。 */
    public BigDecimal dealerGlobalDiscountRate(UUID tid, Long dealerId) {
        if (dealerId == null) return BigDecimal.ZERO;
        String sql = "SELECT discount_rate FROM dealer_global_discounts " +
                "WHERE tenant_id=?1 AND dealer_id=?2 AND deleted_at IS NULL AND status='active' " +
                "AND (valid_from IS NULL OR valid_from <= CURRENT_DATE) " +
                "AND (valid_to IS NULL OR valid_to >= CURRENT_DATE) " +
                "ORDER BY updated_at DESC LIMIT 1";
        var rs = em.createNativeQuery(sql, Tuple.class).setParameter(1, tid).setParameter(2, dealerId).getResultList();
        if (rs.isEmpty()) return BigDecimal.ZERO;
        return bd(((Tuple) rs.get(0)).get("discount_rate"));
    }

    /** 代金券（原始行）。未找到返回 null。 */
    public Tuple voucher(UUID tid, Long voucherId) {
        if (voucherId == null) return null;
        String sql = "SELECT * FROM customer_vouchers WHERE id=?1 AND tenant_id=?2 AND deleted_at IS NULL";
        var rs = em.createNativeQuery(sql, Tuple.class).setParameter(1, voucherId).setParameter(2, tid).getResultList();
        return rs.isEmpty() ? null : (Tuple) rs.get(0);
    }

    /** 该券是否已被有效使用（非 REVERSED），可排除当前订单。 */
    public boolean voucherInUse(UUID tid, Long voucherId, Long excludeOrderId) {
        String sql = "SELECT 1 FROM customer_voucher_usages WHERE tenant_id=?1 AND voucher_id=?2 " +
                "AND status<>'REVERSED' AND (COALESCE(?3, 0) = 0 OR order_id <> ?3) LIMIT 1";
        var rs = em.createNativeQuery(sql).setParameter(1, tid).setParameter(2, voucherId)
                .setParameter(3, excludeOrderId == null ? null : excludeOrderId).getResultList();
        return !rs.isEmpty();
    }

    /** 展开品类/产品线为 SKU 集合（促销唯一性校验用）。 */
    public Set<Long> expandScopeToSkus(UUID tid, Object productId, Object categoryId) {
        Set<Long> ids = new HashSet<>();
        Long pid = toLong(productId);
        Long cid = toLong(categoryId);
        if (pid != null) { ids.add(pid); return ids; }
        if (cid == null) return ids;
        Set<Long> lines = productLineDescendants(tid, cid);
        if (lines.isEmpty()) return ids;
        var rs = em.createNativeQuery("SELECT id FROM products WHERE tenant_id=?1 AND product_line_id IN (?2) AND deleted_at IS NULL", Tuple.class)
                .setParameter(1, tid).setParameter(2, new ArrayList<>(lines)).getResultList();
        for (Object o : rs) ids.add(((Number) ((Tuple) o).get("id")).longValue());
        return ids;
    }

    /** 取所有促销规则（含未生效），供保存/审批做同 SKU 同时段唯一性校验。 */
    @SuppressWarnings("unchecked")
    public List<Tuple> allPromotionRulesForValidation(UUID tid) {
        String sql = "SELECT pr.promotion_id AS promotion_id, p.name AS promo_name, p.promo_type AS promo_type, " +
                "p.valid_from AS valid_from, p.valid_to AS valid_to, p.status AS status, " +
                "pr.rule_detail AS rule_detail " +
                "FROM promotions p JOIN promotion_rules pr ON pr.promotion_id = p.id " +
                "WHERE p.tenant_id=?1 AND p.deleted_at IS NULL";
        return em.createNativeQuery(sql, Tuple.class).setParameter(1, tid).getResultList();
    }

    public enum PriceUse { STANDALONE, BOM_COMPONENT }

    private Price zeroPrice(UUID tenantId, Long productId) {
        Tuple product = product(tenantId, productId);
        BigDecimal rate = product == null || product.get("tax_rate") == null ? new BigDecimal("0.13") : bd(product.get("tax_rate"));
        return new Price(BigDecimal.ZERO, rate, BigDecimal.ZERO);
    }

    private String productLabel(UUID tenantId, Long productId) {
        if (productId == null) return "未知";
        try {
            Tuple t = product(tenantId, productId);
            if (t == null) return String.valueOf(productId);
            Object code = t.get("code");
            Object name = t.get("name_cn");
            if (code != null && name != null) return code + " " + name;
            if (code != null) return String.valueOf(code);
            if (name != null) return String.valueOf(name);
        } catch (Exception ignore) { }
        return String.valueOf(productId);
    }

    private Price findPrice(UUID tid, Long pid, String type, Long partnerId, String context, Long bomParentProductId) {
        String sql = "SELECT sales_price_excl_tax, tax_rate, sales_price FROM product_prices " +
                "WHERE tenant_id=?1 AND product_id=?2 AND partner_type=?3 AND partner_id IS NOT DISTINCT FROM ?4 " +
                "AND price_scope='SALE' AND price_context=?5 AND bom_parent_product_id IS NOT DISTINCT FROM ?6 " +
                "AND status='active' AND deleted_at IS NULL " +
                "AND (valid_from IS NULL OR valid_from<=now()) AND (valid_to IS NULL OR valid_to>=now()) " +
                "ORDER BY updated_at DESC LIMIT 1";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid).setParameter(2, pid).setParameter(3, type).setParameter(4, partnerId)
                .setParameter(5, context).setParameter(6, bomParentProductId);
        List<Tuple> rs = q.getResultList();
        if (rs.isEmpty()) return null;
        Tuple t = rs.get(0);
        BigDecimal excl = bd(t.get("sales_price_excl_tax"));
        BigDecimal rate = bd(t.get("tax_rate"));
        BigDecimal incl = bd(t.get("sales_price"));
        if (incl.signum() == 0 && excl.signum() > 0) incl = excl.multiply(BigDecimal.ONE.add(rate));
        if (excl.signum() == 0 && incl.signum() > 0) excl = incl.divide(BigDecimal.ONE.add(rate), 4, RoundingMode.HALF_UP);
        return new Price(excl, rate, incl);
    }

    public boolean isBom(UUID tid, Long pid) {
        var rs = em.createNativeQuery("SELECT 1 FROM product_bundles WHERE tenant_id=?1 AND product_id=?2 AND version_status='active' AND deleted_at IS NULL LIMIT 1")
                .setParameter(1, tid).setParameter(2, pid).getResultList();
        return !rs.isEmpty();
    }

    public List<Map<String, Object>> bomLines(UUID tid, Long bomProductId, String version) {
        String sql = "SELECT pbl.child_product_id, p.code, p.name_cn, p.spec, pbl.quantity, pb.bom_version, p.is_serial_managed " +
                "FROM product_bundles pb JOIN product_bundle_lines pbl ON pbl.bundle_id=pb.id JOIN products p ON p.id=pbl.child_product_id " +
                "WHERE pb.tenant_id=?1 AND pb.product_id=?2 AND pb.deleted_at IS NULL AND pbl.deleted_at IS NULL AND pb.version_status='active'";
        if (version != null && !version.isBlank()) sql += " AND pb.bom_version=?3";
        sql += " ORDER BY pbl.sort_order, pbl.id";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid); q.setParameter(2, bomProductId);
        if (version != null && !version.isBlank()) q.setParameter(3, version);
        List<Tuple> rs = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", t.get("child_product_id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name_cn"));
            m.put("spec", t.get("spec"));
            m.put("quantity", bd(t.get("quantity")));
            m.put("bomVersion", t.get("bom_version"));
            m.put("serialManaged", Boolean.TRUE.equals(t.get("is_serial_managed")));
            list.add(m);
        }
        return list;
    }

    public String currentBomVersion(UUID tid, Long bomProductId) {
        var rs = em.createNativeQuery("SELECT bom_version FROM product_bundles WHERE tenant_id=?1 AND product_id=?2 AND deleted_at IS NULL AND version_status='active' ORDER BY updated_at DESC LIMIT 1").setParameter(1, tid).setParameter(2, bomProductId).getResultList();
        return rs.isEmpty() ? null : String.valueOf(rs.get(0));
    }

    public Tuple product(UUID tid, Long pid) {
        var rs = em.createNativeQuery("SELECT id,code,name_cn,spec,is_serial_managed,tax_rate,product_line_id FROM products WHERE id=?1 AND tenant_id=?2", Tuple.class).setParameter(1, pid).setParameter(2, tid).getResultList();
        return rs.isEmpty() ? null : (Tuple) rs.get(0);
    }

    public Set<Long> productLineDescendants(UUID tid, Long lineId) {
        Set<Long> ids = new HashSet<>();
        if (lineId == null) return ids;
        LinkedList<Long> queue = new LinkedList<>(List.of(lineId));
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            ids.add(id);
            var rs = em.createNativeQuery("SELECT id FROM product_lines WHERE tenant_id=?1 AND parent_id=?2 AND deleted_at IS NULL").setParameter(1, tid).setParameter(2, id).getResultList();
            for (Object o : rs) queue.add(((Number) o).longValue());
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    public List<Tuple> activePromotions(UUID tid, Long dealerId) {
        return em.createNativeQuery("SELECT id,name,code,promo_type,priority,exclusive,dealer_scope,product_scope FROM promotions WHERE tenant_id=?1 AND status='active' AND deleted_at IS NULL AND (valid_from IS NULL OR valid_from<=?2) AND (valid_to IS NULL OR valid_to>=?2) ORDER BY priority DESC, id ASC", Tuple.class)
                .setParameter(1, tid).setParameter(2, OffsetDateTime.now()).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Tuple> promotionRules(Long promotionId) {
        return em.createNativeQuery("SELECT seq,rule_detail FROM promotion_rules WHERE promotion_id=?1 ORDER BY seq,id", Tuple.class).setParameter(1, promotionId).getResultList();
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

    private BigDecimal bd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(o));
    }
}
