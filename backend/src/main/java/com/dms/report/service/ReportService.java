/*
 * 报表服务：type ∈ contract/order/inventory/sales/authorization/loan/invoice/rebate/discount/return，
 * 通过 native SQL GROUP BY 直接从原表汇总。
 *
 * 物化视图占位（后续增强）：
 *   CREATE MATERIALIZED VIEW mv_dealer_kpi_month AS
 *   SELECT tenant_id, dealer_id, to_char(sales_date, 'YYYYMM') AS period,
 *          SUM(qty) AS total_qty, SUM(amount) AS total_amount
 *   FROM sales_out_facts
 *   GROUP BY tenant_id, dealer_id, period;
 *   REFRESH MATERIALIZED VIEW CONCURRENTLY mv_dealer_kpi_month; -- 每日凌晨
 */
package com.dms.report.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 报表通用查询入口。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> query(String type, Map<String, Object> filters) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (filters == null) filters = new HashMap<>();

        return switch (type) {
            case "contract" -> queryContract(tenantId, filters);
            case "order" -> queryOrder(tenantId, filters);
            case "inventory" -> queryInventory(tenantId, filters);
            case "sales" -> querySales(tenantId, filters);
            case "authorization" -> queryAuthorization(tenantId, filters);
            case "loan" -> queryLoan(tenantId, filters);
            case "invoice" -> queryInvoice(tenantId, filters);
            case "rebate" -> queryRebate(tenantId, filters);
            case "discount" -> queryDiscount(tenantId, filters);
            case "return" -> queryReturn(tenantId, filters);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "未知报表类型: " + type);
        };
    }

    private List<Map<String, Object>> queryContract(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT status, COUNT(*) AS cnt FROM contracts " +
                " WHERE deleted_at IS NULL AND tenant_id = :tenantId " +
                " GROUP BY status ORDER BY status";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryOrder(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT dealer_id, status, COUNT(*) AS cnt, COALESCE(SUM(final_amount),0) AS amount " +
                " FROM orders WHERE deleted_at IS NULL AND tenant_id = :tenantId " +
                " GROUP BY dealer_id, status ORDER BY dealer_id, status";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryInventory(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT dealer_id, product_id, COALESCE(SUM(qty),0) AS total_qty " +
                " FROM inventory WHERE tenant_id = :tenantId " +
                " GROUP BY dealer_id, product_id ORDER BY dealer_id, product_id";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> querySales(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT dealer_id, to_char(sales_date,'YYYY-MM') AS period, " +
                "        COALESCE(SUM(qty),0) AS total_qty, COALESCE(SUM(amount),0) AS total_amount " +
                " FROM sales_out_facts WHERE tenant_id = :tenantId " +
                " GROUP BY dealer_id, period ORDER BY dealer_id, period";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryAuthorization(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT dealer_id, auth_type, COUNT(*) AS cnt " +
                " FROM authorizations WHERE deleted_at IS NULL AND tenant_id = :tenantId " +
                " GROUP BY dealer_id, auth_type ORDER BY dealer_id, auth_type";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryLoan(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT status, COUNT(*) AS cnt FROM loans " +
                " WHERE deleted_at IS NULL AND tenant_id = :tenantId " +
                " GROUP BY status ORDER BY status";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryInvoice(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT to_char(invoice_date,'YYYY-MM') AS period, " +
                "        COALESCE(SUM(amount),0) AS total_amount, COUNT(*) AS cnt " +
                " FROM purchase_invoices WHERE deleted_at IS NULL AND tenant_id = :tenantId " +
                " GROUP BY period ORDER BY period";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryRebate(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT dealer_id, period_yyyymm, net_rebate " +
                " FROM rebate_previews WHERE tenant_id = :tenantId " +
                " ORDER BY dealer_id, period_yyyymm";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryDiscount(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT o.dealer_id, COALESCE(SUM(o.discount_amount),0) AS total_discount " +
                " FROM orders o WHERE o.deleted_at IS NULL AND o.tenant_id = :tenantId " +
                " GROUP BY o.dealer_id ORDER BY total_discount DESC";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    private List<Map<String, Object>> queryReturn(UUID tenantId, Map<String, Object> filters) {
        String sql = "SELECT dealer_id, rma_type, COUNT(*) AS cnt, COALESCE(SUM(amount),0) AS amount " +
                " FROM rma_orders WHERE deleted_at IS NULL AND tenant_id = :tenantId " +
                " GROUP BY dealer_id, rma_type ORDER BY dealer_id";
        return exec(sql, Map.of("tenantId", tenantId));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> exec(String sql, Map<String, ?> params) {
        Query q = entityManager.createNativeQuery(sql, jakarta.persistence.Tuple.class);
        params.forEach((k, v) -> q.setParameter(k, v));
        List<jakarta.persistence.Tuple> tuples = q.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (jakarta.persistence.Tuple t : tuples) {
            Map<String, Object> row = new HashMap<>();
            t.getElements().forEach(e -> row.put(e.getAlias(), t.get(e.getAlias())));
            rows.add(row);
        }
        return rows;
    }
}
