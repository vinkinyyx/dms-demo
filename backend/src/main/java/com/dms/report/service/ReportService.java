/*
 * Report service for v3.11 documented report center.
 */
package com.dms.report.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> query(String type, Map<String, Object> filters) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        if (filters == null) filters = new HashMap<>();
        int limit = intParam(filters, "limit", Math.min(intParam(filters, "size", 50), 500));
        Map<String, Object> params = new HashMap<>(filters);
        params.put("tenantId", tenantId);
        params.put("limit", limit);
        return switch (type) {
            case "sales-ranking" -> rows("""
                SELECT d.id AS dealer_id, d.name AS dealer_name, COUNT(DISTINCT o.id) AS order_count,
                       COALESCE(SUM(o.final_amount),0) AS amount, COALESCE(SUM(f.qty),0) AS qty
                FROM dealers d
                LEFT JOIN orders o ON o.dealer_id=d.id AND o.tenant_id=:tenantId AND o.deleted_at IS NULL
                LEFT JOIN sales_out_facts f ON f.dealer_id=d.id AND f.tenant_id=:tenantId
                WHERE d.tenant_id=:tenantId AND d.deleted_at IS NULL
                GROUP BY d.id,d.name ORDER BY amount DESC, qty DESC LIMIT :limit
                """, params);
            case "product-top10" -> rows("""
                SELECT p.id AS product_id, p.code AS product_code, COALESCE(NULLIF(p.name_cn,''),p.name_en,p.code) AS product_name,
                       COALESCE(SUM(f.qty),0) AS qty, COALESCE(SUM(f.amount),0) AS amount
                FROM products p
                LEFT JOIN sales_out_facts f ON f.product_id=p.id AND f.tenant_id=:tenantId
                WHERE p.tenant_id=:tenantId AND p.deleted_at IS NULL
                GROUP BY p.id,p.code,p.name_cn,p.name_en
                ORDER BY amount DESC, qty DESC LIMIT :limit
                """, params);
            case "inventory-turnover" -> rows("""
                SELECT p.id AS product_id,p.code AS product_code,COALESCE(NULLIF(p.name_cn,''),p.name_en,p.code) AS product_name,
                       COALESCE(SUM(i.qty),0) AS stock_qty,
                       COALESCE((SELECT SUM(f.qty) FROM sales_out_facts f WHERE f.tenant_id=:tenantId AND f.product_id=p.id AND f.sales_date >= current_date - interval '90 days'),0) AS outgoing_90d,
                       COALESCE((SELECT SUM(rl.received_qty) FROM receipt_lines rl JOIN receipts r ON r.id=rl.receipt_id WHERE r.tenant_id=:tenantId AND rl.product_id=p.id AND r.received_at >= current_timestamp - interval '90 days'),0) AS incoming_90d
                FROM products p LEFT JOIN inventory i ON i.product_id=p.id AND i.tenant_id=:tenantId
                WHERE p.tenant_id=:tenantId AND p.deleted_at IS NULL
                GROUP BY p.id,p.code,p.name_cn,p.name_en ORDER BY outgoing_90d DESC, stock_qty DESC LIMIT :limit
                """, params);
            case "order-trace" -> rows("""
                SELECT o.id,o.code,o.dealer_id,d.name AS dealer_name,o.status,o.final_amount AS amount,o.expected_date,o.created_at,o.updated_at
                FROM orders o LEFT JOIN dealers d ON d.id=o.dealer_id
                WHERE o.tenant_id=:tenantId AND o.deleted_at IS NULL
                ORDER BY o.updated_at DESC NULLS LAST, o.id DESC LIMIT :limit
                """, params);
            case "receivables" -> rows("""
                SELECT d.id AS dealer_id,d.name AS dealer_name,
                       COALESCE(SUM(o.final_amount) FILTER (WHERE o.status NOT IN ('CANCELLED','REJECTED','DRAFT')),0) AS order_amount,
                       COALESCE(SUM(o.final_amount) FILTER (WHERE o.status IN ('SHIPPED','COMPLETED','RECEIVED')),0) AS shipped_amount,
                       COALESCE(SUM(o.final_amount) FILTER (WHERE o.status='COMPLETED'),0) AS settled_amount
                FROM dealers d LEFT JOIN orders o ON o.dealer_id=d.id AND o.tenant_id=:tenantId AND o.deleted_at IS NULL
                WHERE d.tenant_id=:tenantId AND d.deleted_at IS NULL
                GROUP BY d.id,d.name ORDER BY order_amount DESC LIMIT :limit
                """, params);
            case "surgery-stats" -> rows("""
                SELECT d.id AS dealer_id,d.name AS dealer_name,h.name AS hospital_name,COUNT(s.id) AS report_count,
                       COALESCE(SUM((SELECT COALESCE(SUM(l.qty),0) FROM surgery_report_lines l WHERE l.report_id=s.id)),0) AS implant_qty
                FROM surgery_reports s
                LEFT JOIN dealers d ON d.id=s.dealer_id LEFT JOIN hospitals h ON h.id=s.terminal_id
                WHERE s.tenant_id=:tenantId AND s.deleted_at IS NULL
                GROUP BY d.id,d.name,h.name ORDER BY report_count DESC, implant_qty DESC LIMIT :limit
                """, params);
            case "sales" -> rows("""
                SELECT f.dealer_id,d.name AS dealer_id_text,p.code AS product_code,COALESCE(NULLIF(p.name_cn,''),p.name_en,p.code) AS product_name,
                       f.sales_date AS date, SUM(f.qty) AS qty, SUM(f.amount) AS amount
                FROM sales_out_facts f
                LEFT JOIN dealers d ON d.id=f.dealer_id LEFT JOIN products p ON p.id=f.product_id
                WHERE f.tenant_id=:tenantId GROUP BY f.dealer_id,d.name,p.code,p.name_cn,p.name_en,f.sales_date
                ORDER BY f.sales_date DESC LIMIT :limit
                """, params);
            case "contract" -> rows("""
                SELECT c.id,c.code,d.name AS dealer_name,c.category,c.status,c.valid_from,c.valid_to,c.created_at
                FROM contracts c LEFT JOIN dealers d ON d.id=c.dealer_id
                WHERE c.tenant_id=:tenantId AND c.deleted_at IS NULL ORDER BY c.updated_at DESC NULLS LAST,c.id DESC LIMIT :limit
                """, params);
            case "authorization" -> rows("""
                SELECT a.id,a.dealer_id,d.name AS dealer_name,a.auth_type,a.status,a.valid_from,a.valid_to,a.created_at
                FROM authorizations a LEFT JOIN dealers d ON d.id=a.dealer_id
                WHERE a.tenant_id=:tenantId AND a.deleted_at IS NULL
                  AND (a.valid_to < current_date OR a.status IN ('expired','pending_approval','rejected'))
                ORDER BY a.valid_to ASC LIMIT :limit
                """, params);
            case "loan" -> rows("""
                SELECT status, COUNT(*) AS cnt FROM loans WHERE tenant_id=:tenantId AND deleted_at IS NULL
                GROUP BY status ORDER BY status LIMIT :limit
                """, params);
            case "rebate" -> rows("""
                SELECT dealer_id,period_yyyymm,net_rebate FROM rebate_previews WHERE tenant_id=:tenantId
                ORDER BY period_yyyymm DESC NULLS LAST, dealer_id LIMIT :limit
                """, params);
            case "discount" -> rows("""
                SELECT o.dealer_id,d.name AS dealer_name,COUNT(*) AS order_count,COALESCE(SUM(o.discount_amount),0) AS total_discount
                FROM orders o LEFT JOIN dealers d ON d.id=o.dealer_id
                WHERE o.tenant_id=:tenantId AND o.deleted_at IS NULL
                GROUP BY o.dealer_id,d.name ORDER BY total_discount DESC LIMIT :limit
                """, params);
            case "rebate-discount" -> rows("""
                SELECT o.dealer_id,d.name AS dealer_name,COALESCE(SUM(o.discount_amount),0) AS total_discount,
                       COALESCE((SELECT SUM(r.net_rebate) FROM rebate_previews r WHERE r.tenant_id=:tenantId AND r.dealer_id=o.dealer_id),0) AS net_rebate
                FROM orders o LEFT JOIN dealers d ON d.id=o.dealer_id
                WHERE o.tenant_id=:tenantId AND o.deleted_at IS NULL
                GROUP BY o.dealer_id,d.name ORDER BY total_discount DESC LIMIT :limit
                """, params);
            case "inventory-aging" -> rows("""
                SELECT i.id,i.dealer_id,d.name AS dealer_name,p.code AS product_code,COALESCE(NULLIF(p.name_cn,''),p.name_en,p.code) AS product_name,
                       i.batch_no,i.prod_date,i.exp_date,i.qty,
                       CASE WHEN i.exp_date IS NULL THEN 0 ELSE current_date - i.exp_date END AS aging_days
                FROM inventory i LEFT JOIN dealers d ON d.id=i.dealer_id LEFT JOIN products p ON p.id=i.product_id
                WHERE i.tenant_id=:tenantId AND i.qty > 0 ORDER BY aging_days DESC, i.updated_at DESC LIMIT :limit
                """, params);
            case "order-approval" -> rows("""
                SELECT ai.id,ai.business_id,ai.business_type,ai.status,ai.submitter_id,ai.current_node_name,
                       ai.created_at,ai.finished_at,
                       EXTRACT(EPOCH FROM (COALESCE(ai.finished_at,now())-ai.created_at))/3600 AS approval_hours
                FROM approval_instances ai WHERE ai.tenant_id=:tenantId
                ORDER BY ai.started_at DESC LIMIT :limit
                """, params);
            case "order" -> rows("""
                SELECT dealer_id,status,COUNT(*) AS cnt,COALESCE(SUM(final_amount),0) AS amount
                FROM orders WHERE tenant_id=:tenantId AND deleted_at IS NULL
                GROUP BY dealer_id,status ORDER BY dealer_id,status LIMIT :limit
                """, params);
            case "inventory" -> rows("""
                SELECT dealer_id,product_id,COALESCE(SUM(qty),0) AS total_qty FROM inventory WHERE tenant_id=:tenantId
                GROUP BY dealer_id,product_id ORDER BY dealer_id,product_id LIMIT :limit
                """, params);
            case "invoice" -> rows("""
                SELECT to_char(invoice_date,'YYYY-MM') AS period,COALESCE(SUM(amount),0) AS total_amount,COUNT(*) AS cnt
                FROM purchase_invoices WHERE tenant_id=:tenantId AND deleted_at IS NULL
                GROUP BY period ORDER BY period LIMIT :limit
                """, params);
            case "return" -> rows("""
                SELECT dealer_id,rma_type,COUNT(*) AS cnt,COALESCE(SUM(amount),0) AS amount
                FROM rma_orders WHERE tenant_id=:tenantId AND deleted_at IS NULL
                GROUP BY dealer_id,rma_type ORDER BY dealer_id,rma_type LIMIT :limit
                """, params);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "未知报表类型: " + type);
        };
    }

    private int intParam(Map<String, Object> filters, String key, int dflt) {
        Object v = filters.get(key);
        if (v == null) return dflt;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return dflt; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String sql, Map<String, ?> params) {
        Query q = entityManager.createNativeQuery(sql, Tuple.class);
        params.forEach((k, v) -> {
            if (sql.contains(":" + k)) q.setParameter(k, v);
        });
        List<Tuple> tuples = q.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            Map<String, Object> row = new HashMap<>();
            t.getElements().forEach(e -> row.put(e.getAlias(), t.get(e.getAlias())));
            rows.add(row);
        }
        return rows;
    }
}
