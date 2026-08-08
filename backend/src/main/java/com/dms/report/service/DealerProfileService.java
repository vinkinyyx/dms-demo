/*
 * Dealer 360 profile service.
 */
package com.dms.report.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.repository.DealerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealerProfileService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DealerRepository dealerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<String, Object> getBasic(Long dealerId) {
        Dealer d = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "经销商不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("level", d.getLevel());
        m.put("legalPerson", d.getLegalPerson());
        m.put("uscNo", d.getUscNo());
        m.put("regionId", d.getRegionId());
        m.put("gspStatus", d.getGspStatus());
        m.put("gspExpire", d.getGspExpire());
        m.put("status", d.getStatus());
        m.put("contactName", d.getContactName());
        m.put("contactPhone", d.getContactPhone());
        m.put("contactEmail", d.getContactEmail());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getKpi(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        String currentYm = YearMonth.now().format(YM);
        String prevYm = YearMonth.now().minusMonths(1).format(YM);
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("period", currentYm);

        BigDecimal monthTarget = bd("SELECT COALESCE(target_amount,0) FROM rebate_previews WHERE tenant_id=?1 AND dealer_id=?2 AND period_yyyymm=?3", tenantId, dealerId, currentYm);
        BigDecimal monthActual = bd("SELECT COALESCE(SUM(amount_incl_tax),0) FROM orders WHERE deleted_at IS NULL AND tenant_id=?1 AND dealer_id=?2 AND created_at >= ?3", tenantId, dealerId, monthStart.atStartOfDay());
        BigDecimal prevActual = bd("SELECT COALESCE(actual_amount, target_amount * achievement_rate, 0) FROM rebate_previews WHERE tenant_id=?1 AND dealer_id=?2 AND period_yyyymm=?3", tenantId, dealerId, prevYm);

        BigDecimal yearTarget = bd("SELECT COALESCE(SUM(target_amount),0) FROM rebate_previews WHERE tenant_id=?1 AND dealer_id=?2 AND period_yyyymm >= ?3", tenantId, dealerId, YearMonth.from(yearStart).format(YM));
        BigDecimal ytdActual = bd("SELECT COALESCE(SUM(amount_incl_tax),0) FROM orders WHERE deleted_at IS NULL AND tenant_id=?1 AND dealer_id=?2 AND created_at >= ?3", tenantId, dealerId, yearStart.atStartOfDay());
        BigDecimal ytdRebate = bd("SELECT COALESCE(SUM(net_rebate),0) FROM rebate_previews WHERE tenant_id=?1 AND dealer_id=?2 AND period_yyyymm >= ?3", tenantId, dealerId, YearMonth.from(yearStart).format(YM));

        long monthOrders = lng("SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL AND tenant_id=?1 AND dealer_id=?2 AND created_at >= ?3", tenantId, dealerId, monthStart.atStartOfDay());
        long ytdOrders = lng("SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL AND tenant_id=?1 AND dealer_id=?2 AND created_at >= ?3", tenantId, dealerId, yearStart.atStartOfDay());
        long activeSku = lng("SELECT COUNT(DISTINCT product_id) FROM inventory WHERE tenant_id=?1 AND dealer_id=?2 AND qty <> 0", tenantId, dealerId);
        long activeContracts = lng("SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL AND tenant_id=?1 AND dealer_id=?2 AND status IN ('effective','approved','signed') AND valid_to >= CURRENT_DATE", tenantId, dealerId);
        long expiringContracts = lng("SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL AND tenant_id=?1 AND dealer_id=?2 AND status IN ('effective','approved','signed') AND valid_to BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '90 days'", tenantId, dealerId);
        BigDecimal returnAmount = bd("SELECT COALESCE(SUM(amount),0) FROM rma_orders WHERE deleted_at IS NULL AND tenant_id=?1 AND dealer_id=?2 AND created_at >= ?3", tenantId, dealerId, yearStart.atStartOfDay());
        BigDecimal invQty = bd("SELECT COALESCE(SUM(i.qty),0) FROM inventory i WHERE i.tenant_id=?1 AND i.dealer_id=?2", tenantId, dealerId);
        BigDecimal invAmount = bd("SELECT COALESCE(SUM(i.qty * COALESCE(p.current_price,0)),0) FROM inventory i LEFT JOIN products p ON p.id=i.product_id WHERE i.tenant_id=?1 AND i.dealer_id=?2", tenantId, dealerId);
        BigDecimal qualifiedRate = ratio(bd("SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id=?1 AND dealer_id=?2 AND stock_status='QUALIFIED'", tenantId, dealerId), invQty);

        BigDecimal monthAch = ratio(monthActual, monthTarget);
        BigDecimal ytdAch = ratio(ytdActual, yearTarget);
        BigDecimal mom = ratio(monthActual.subtract(prevActual), prevActual);
        BigDecimal gap = monthTarget.subtract(monthActual);

        kpi.put("monthTarget", monthTarget);
        kpi.put("monthActual", monthActual);
        kpi.put("monthGap", gap);
        kpi.put("monthAchievement", monthAch);
        kpi.put("monthOrders", monthOrders);
        kpi.put("ytdTarget", yearTarget);
        kpi.put("ytdActual", ytdActual);
        kpi.put("ytdGap", yearTarget.subtract(ytdActual));
        kpi.put("ytdAchievement", ytdAch);
        kpi.put("ytdOrders", ytdOrders);
        kpi.put("ytdRebate", ytdRebate);
        kpi.put("momRate", mom);
        kpi.put("prevActual", prevActual);
        kpi.put("returnAmount", returnAmount);
        kpi.put("returnRate", ratio(returnAmount, ytdActual));
        kpi.put("inventoryQty", invQty);
        kpi.put("inventoryAmount", invAmount);
        kpi.put("inventorySku", activeSku);
        kpi.put("qualifiedRate", qualifiedRate);
        kpi.put("activeContracts", activeContracts);
        kpi.put("expiringContracts", expiringContracts);
        return kpi;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAchievement(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT period_yyyymm, target_amount, actual_amount, achievement_rate, " +
                " actual_amount - target_amount AS gap_amount, tier_hit, " +
                " LAG(actual_amount) OVER (ORDER BY period_yyyymm) AS prev_actual, " +
                " gross_rebate, net_rebate " +
                "FROM rebate_previews WHERE tenant_id=:t AND dealer_id=:d ORDER BY period_yyyymm", tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRebate(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT period_yyyymm, target_amount, actual_amount, achievement_rate, tier_hit, " +
                " gross_rebate, COALESCE(CAST(NULLIF(regexp_replace(COALESCE(deductions->>'amount', CAST(deductions AS text), ''), '[^0-9.]', '', 'g'), '') AS numeric), 0) AS deduction_amount, " +
                " net_rebate, " +
                " CASE WHEN net_rebate IS NULL OR net_rebate = 0 THEN '待结算' WHEN period_yyyymm <= to_char(CURRENT_DATE - INTERVAL '30 days','YYYYMM') THEN '已结算' ELSE '预提中' END AS settlement_status " +
                "FROM rebate_previews WHERE tenant_id=:t AND dealer_id=:d ORDER BY period_yyyymm", tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getContracts(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT id, code, contract_name, category, contract_type, vendor_party, dealer_party, sign_city, " +
                " valid_from, valid_to, (valid_to - valid_from) AS term_days, status, " +
                " target_amount, signed_amount, rebate_rate, payment_terms, settlement_cycle, delivery_terms, " +
                " business_scope, owner_name, owner_phone, renew_before_days, terminated_at, " +
                " dealer_signed_at, vendor_signed_at, ca_serial_no, pdf_url, " +
                " (SELECT COUNT(*) FROM contract_signatures cs WHERE cs.contract_id = contracts.id) AS sign_count, " +
                " (SELECT COUNT(*) FROM contract_attachments ca WHERE ca.ref_type = 'CONTRACT' AND ca.ref_id = contracts.id) AS attach_count " +
                "FROM contracts WHERE deleted_at IS NULL AND tenant_id=:t AND dealer_id=:d ORDER BY valid_from DESC, id DESC", tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInventory(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT i.product_id, p.code AS product_code, p.name_cn AS product_name_cn, " +
                " p.spec AS product_spec, p.unit AS product_unit, c.name AS category_name, w.name AS warehouse_name, " +
                " i.batch_no, SUM(i.qty) AS qty, SUM(i.qty * COALESCE(p.current_price,0)) AS amount, " +
                " COALESCE(AVG(p.current_price),0) AS unit_price, i.stock_status, " +
                " MIN(i.prod_date) AS prod_date, MIN(i.exp_date) AS exp_date, MIN(i.warehouse_id) AS warehouse_id, " +
                " MIN(i.exp_date) - CURRENT_DATE AS days_to_expire " +
                "FROM inventory i LEFT JOIN products p ON p.id = i.product_id " +
                "LEFT JOIN product_categories c ON c.id = p.category_id " +
                "LEFT JOIN warehouses w ON w.id = i.warehouse_id " +
                "WHERE i.tenant_id=:t AND i.dealer_id=:d " +
                "GROUP BY i.product_id, p.code, p.name_cn, p.spec, p.unit, c.name, w.name, i.batch_no, i.stock_status " +
                "ORDER BY amount DESC NULLS LAST, i.product_id, i.batch_no", tenantId, dealerId);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return BigDecimal.ZERO;
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal bd(String sql, Object... args) {
        try {
            Query q = entityManager.createNativeQuery(sql);
            for (int i = 0; i < args.length; i++) q.setParameter(i + 1, args[i]);
            Object v = q.getSingleResult();
            return v == null ? BigDecimal.ZERO : (BigDecimal) v;
        } catch (Exception e) {
            log.warn("Dealer profile scalar failed: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private long lng(String sql, Object... args) {
        try {
            Query q = entityManager.createNativeQuery(sql);
            for (int i = 0; i < args.length; i++) q.setParameter(i + 1, args[i]);
            Object v = q.getSingleResult();
            return v == null ? 0L : ((Number) v).longValue();
        } catch (Exception e) {
            log.warn("Dealer profile long scalar failed: {}", e.getMessage());
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String sql, UUID tenantId, Long dealerId) {
        Query q = entityManager.createNativeQuery(sql, Tuple.class);
        q.setParameter("t", tenantId);
        q.setParameter("d", dealerId);
        List<Tuple> tuples = q.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (jakarta.persistence.TupleElement<?> e : t.getElements()) {
                String alias = e.getAlias();
                row.put(toCamel(alias), t.get(alias));
            }
            rows.add(row);
        }
        return rows;
    }

    private static String toCamel(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }
}
