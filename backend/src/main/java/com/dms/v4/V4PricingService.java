package com.dms.v4;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class V4PricingService {
    private final EntityManager em;

    public record Price(BigDecimal excl, BigDecimal rate, BigDecimal incl) {}

    public Price salesPrice(UUID tenantId, Long productId, Long dealerId) {
        return salesPrice(tenantId, productId, dealerId, PriceUse.STANDALONE, null);
    }

    public Price salesPrice(UUID tenantId, Long productId, Long dealerId, PriceUse use, Long bomParentProductId) {
        if (use == PriceUse.BOM_COMPONENT) {
            Price p = findPrice(tenantId, productId, "DEALER", dealerId, "BOM_COMPONENT", bomParentProductId);
            if (p == null) p = findPrice(tenantId, productId, "GLOBAL", 0L, "BOM_COMPONENT", bomParentProductId);
            if (p == null) p = findPrice(tenantId, productId, "GLOBAL", null, "BOM_COMPONENT", bomParentProductId);
            return p == null ? zeroPrice(tenantId, productId) : p;
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
        if (excl.signum() == 0 && incl.signum() > 0) excl = incl.divide(BigDecimal.ONE.add(rate), 4, java.math.RoundingMode.HALF_UP);
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

    private BigDecimal bd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(o));
    }
}
