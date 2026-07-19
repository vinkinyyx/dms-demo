/*
 * 补齐类接口：促销审批、返利计算、借货单、Excel 导出
 * 覆盖 US-B-Promo-07/10, US-C-10, US-B-20, US-C-08
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MiscSupplementController {

    private final EntityManager em;

    // ============ US-B-Promo-07 促销审批 + US-B-Promo-10 审计 ============

    @PostMapping("/api/promotions/{id}/submit")
    @Transactional
    public ApiResponse<Map<String, Object>> promoSubmit(@PathVariable Long id) {
        return promoStatus(id, "SUBMITTED", "PROMO_SUBMIT");
    }

    @PostMapping("/api/promotions/{id}/approve")
    @Transactional
    public ApiResponse<Map<String, Object>> promoApprove(@PathVariable Long id) {
        return promoStatus(id, "active", "PROMO_APPROVE");
    }

    @PostMapping("/api/promotions/{id}/reject")
    @Transactional
    public ApiResponse<Map<String, Object>> promoReject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "驳回" : String.valueOf(body.getOrDefault("reason", "驳回"));
        Map<String, Object> res = promoStatus(id, "draft", "PROMO_REJECT").getData();
        if (res != null) res.put("reason", reason);
        return ApiResponse.ok(res);
    }

    private ApiResponse<Map<String, Object>> promoStatus(Long id, String newStatus, String action) {
        try {
            var upd = em.createNativeQuery(
                    "UPDATE promotions SET status = ?1, updated_at = now() WHERE id = ?2 AND tenant_id = ?3");
            upd.setParameter(1, newStatus).setParameter(2, id).setParameter(3, TenantContext.getTenantId());
            int n = upd.executeUpdate();
            if (n == 0) return ApiResponse.fail(40404, "促销不存在");

            // 审计（US-B-Promo-10）
            em.createNativeQuery(
                    "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, ip, at_time) " +
                    "VALUES (?1, ?2, ?3, 'promotion', ?4, '127.0.0.1', now())")
                    .setParameter(1, TenantContext.getTenantId())
                    .setParameter(2, TenantContext.getUserId())
                    .setParameter(3, action)
                    .setParameter(4, String.valueOf(id))
                    .executeUpdate();

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("promoId", id);
            res.put("status", newStatus);
            res.put("action", action);
            res.put("at", LocalDateTime.now().toString());
            return ApiResponse.ok(res);
        } catch (Exception e) {
            return ApiResponse.fail(50000, "失败：" + e.getMessage());
        }
    }

    // ============ US-C-10 返利计算引擎（3-5 段分段公式）============

    /**
     * 返利计算：给定经销商 ID 和期间，按分段公式计算返利
     * 公式（Mock 默认）：
     *   0 ~ 100k     → 0%
     *   100k ~ 500k  → 2%
     *   500k ~ 1M    → 4%
     *   1M ~ 3M      → 6%
     *   > 3M         → 8%
     */
    @GetMapping("/api/rebates/calculate")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> calculateRebate(
            @RequestParam Long dealerId,
            @RequestParam(required = false) String period) {
        UUID tenantId = TenantContext.getTenantId();
        // 累计销售额
        BigDecimal total;
        try {
            var q = em.createNativeQuery(
                    "SELECT COALESCE(SUM(amount_incl_tax), 0) FROM orders " +
                    "WHERE tenant_id = ?1 AND dealer_id = ?2 AND status IN ('APPROVED','COMPLETED')");
            q.setParameter(1, tenantId).setParameter(2, dealerId);
            total = new BigDecimal(String.valueOf(q.getSingleResult()));
        } catch (Exception e) {
            total = BigDecimal.ZERO;
        }

        // 分段计算
        BigDecimal[] tiers = {
                new BigDecimal("100000"),
                new BigDecimal("500000"),
                new BigDecimal("1000000"),
                new BigDecimal("3000000")
        };
        BigDecimal[] rates = {
                new BigDecimal("0.02"),
                new BigDecimal("0.04"),
                new BigDecimal("0.06"),
                new BigDecimal("0.08")
        };

        BigDecimal rebate = BigDecimal.ZERO;
        List<Map<String, Object>> segments = new ArrayList<>();
        BigDecimal prev = BigDecimal.ZERO;
        for (int i = 0; i < tiers.length; i++) {
            if (total.compareTo(tiers[i]) <= 0) {
                BigDecimal seg = total.subtract(prev).max(BigDecimal.ZERO);
                if (seg.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal r = seg.multiply(rates[i]);
                    rebate = rebate.add(r);
                    segments.add(seg(prev, total, rates[i], r));
                }
                prev = total;
                break;
            } else {
                BigDecimal seg = tiers[i].subtract(prev);
                BigDecimal r = seg.multiply(rates[i]);
                rebate = rebate.add(r);
                segments.add(seg(prev, tiers[i], rates[i], r));
                prev = tiers[i];
            }
        }
        if (total.compareTo(tiers[tiers.length - 1]) > 0) {
            BigDecimal seg = total.subtract(prev);
            BigDecimal r = seg.multiply(rates[rates.length - 1]);
            rebate = rebate.add(r);
            segments.add(seg(prev, total, rates[rates.length - 1], r));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dealerId", dealerId);
        data.put("period", period == null ? "累计" : period);
        data.put("totalSales", total);
        data.put("totalRebate", rebate);
        data.put("effectiveRate", total.compareTo(BigDecimal.ZERO) > 0 ?
                rebate.multiply(new BigDecimal("100"))
                      .divide(total, 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
        data.put("segments", segments);
        return ApiResponse.ok(data);
    }

    private Map<String, Object> seg(BigDecimal from, BigDecimal to, BigDecimal rate, BigDecimal rebate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("rate", rate);
        m.put("rebate", rebate);
        return m;
    }

    // ============ US-B-20 借货单 ============

    @GetMapping("/api/loans")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> listLoans(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;
        try {
            var qCnt = em.createNativeQuery("SELECT COUNT(*) FROM loans WHERE tenant_id = ?1");
            qCnt.setParameter(1, tid);
            long total = ((Number) qCnt.getSingleResult()).longValue();

            var q = em.createNativeQuery(
                    "SELECT id, code, lender_dealer_id, borrower_dealer_id, status, reason, created_at, completed_at " +
                    "FROM loans WHERE tenant_id = ?1 ORDER BY created_at DESC LIMIT ?2 OFFSET ?3", Tuple.class);
            q.setParameter(1, tid).setParameter(2, size).setParameter(3, offset);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.get("id"));
                m.put("code", t.get("code"));
                m.put("lenderDealerId", t.get("lender_dealer_id"));
                m.put("borrowerDealerId", t.get("borrower_dealer_id"));
                m.put("status", t.get("status"));
                m.put("reason", t.get("reason"));
                m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
                m.put("completedAt", com.dms.common.util.DateFmt.fmt(t.get("completed_at")));
                list.add(m);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", total);
            data.put("page", page);
            data.put("size", size);
            data.put("list", list);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", 0);
            data.put("list", Collections.emptyList());
            data.put("note", "借货单加载失败：" + e.getMessage());
            return ApiResponse.ok(data);
        }
    }

    @PostMapping("/api/loans")
    @Transactional
    public ApiResponse<Map<String, Object>> createLoan(@RequestBody Map<String, Object> body) {
        try {
            String code = "LOAN-" + System.currentTimeMillis();
            em.createNativeQuery(
                    "INSERT INTO loans (tenant_id, code, lender_dealer_id, borrower_dealer_id, status, reason, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, 'PENDING', ?5, now(), now())")
                .setParameter(1, TenantContext.getTenantId())
                .setParameter(2, code)
                .setParameter(3, body.get("lenderDealerId"))
                .setParameter(4, body.get("borrowerDealerId"))
                .setParameter(5, body.getOrDefault("reason", ""))
                .executeUpdate();
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("code", code);
            return ApiResponse.ok(res);
        } catch (Exception e) {
            return ApiResponse.fail(50000, "创建失败：" + e.getMessage());
        }
    }

    // ============ US-S-03 SEED_ENABLED 开关（只读展示当前状态）============

    @GetMapping("/api/system-ops/seed-status")
    public ApiResponse<Map<String, Object>> seedStatus() {
        Map<String, Object> res = new LinkedHashMap<>();
        String seedEnabled = System.getenv("SEED_ENABLED");
        res.put("seedEnabled", seedEnabled == null ? "true (default)" : seedEnabled);
        res.put("note", "SEED_ENABLED 由 Docker 环境变量控制。设为 false 则跳过 V7 演示数据填充。");
        try {
            var q = em.createNativeQuery("SELECT COUNT(*) FROM users");
            res.put("usersCount", ((Number) q.getSingleResult()).longValue());
        } catch (Exception ignored) { res.put("usersCount", 0); }
        return ApiResponse.ok(res);
    }

    // ============ US-C-08 Excel 导出（CSV 兼容 Excel）============

    @GetMapping("/api/reports/{reportKey}/export-csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportReportCsv(@PathVariable String reportKey) {
        UUID tid = TenantContext.getTenantId();
        String sql;
        String[] headers;
        switch (reportKey) {
            case "orders":
                sql = "SELECT id, code, order_type, dealer_id, amount_incl_tax, final_amount, status, expected_date " +
                      "FROM orders WHERE tenant_id = ?1 ORDER BY id DESC LIMIT 5000";
                headers = new String[]{"编号","订单号","类型","经销商","含税金额","最终金额","状态","期望到货"};
                break;
            case "dealers":
                sql = "SELECT id, code, name, level, contact_name, contact_phone, status " +
                      "FROM dealers WHERE tenant_id = ?1 ORDER BY id LIMIT 5000";
                headers = new String[]{"编号","编码","名称","级别","联系人","电话","状态"};
                break;
            case "products":
                sql = "SELECT id, code, name_cn, spec, unit, current_price, tax_rate, status " +
                      "FROM products WHERE tenant_id = ?1 ORDER BY id LIMIT 5000";
                headers = new String[]{"编号","编码","中文名称","规格","单位","单价","税率","状态"};
                break;
            case "contracts":
                sql = "SELECT id, code, dealer_id, category, valid_from, valid_to, status " +
                      "FROM contracts WHERE tenant_id = ?1 ORDER BY id LIMIT 5000";
                headers = new String[]{"编号","合同号","经销商","分类","生效","失效","状态"};
                break;
            case "inventory":
                sql = "SELECT id, product_id, warehouse_id, batch_no, serial_no, qty, exp_date " +
                      "FROM inventory WHERE tenant_id = ?1 ORDER BY id LIMIT 5000";
                headers = new String[]{"编号","产品","仓库","批次","序列号","数量","到期"};
                break;
            default:
                return ResponseEntity.badRequest().body("未知报表: " + reportKey);
        }
        try {
            var q = em.createNativeQuery(sql);
            q.setParameter(1, tid);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();

            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", headers)).append('\n');
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) csv.append(',');
                    Object v = row[i];
                    if (v == null) csv.append("");
                    else csv.append(String.valueOf(v).replace(",", "，").replace("\n", " "));
                }
                csv.append('\n');
            }
            String filename = "report-" + reportKey + "-" + System.currentTimeMillis() + ".csv";
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body("\ufeff" + csv);  // BOM for Excel UTF-8
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("导出失败: " + e.getMessage());
        }
    }
}
