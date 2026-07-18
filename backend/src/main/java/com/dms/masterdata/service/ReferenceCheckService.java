/*
 * 主数据引用检查器（US-A-02）
 * 停用主数据前必须检查是否被业务表引用
 */
package com.dms.masterdata.service;

import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ReferenceCheckService {

    @PersistenceContext
    private EntityManager em;

    /** 检查产品引用 */
    public Map<String, Long> productReferences(Long productId) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Long> refs = new LinkedHashMap<>();
        refs.put("订单明细", countByProduct("order_lines", productId, tid));
        refs.put("库存",     countByProduct("inventory", productId, tid));
        refs.put("销售出库明细", countByProduct("sales_out_lines", productId, tid));
        refs.put("授权记录", countByProduct("authorizations", productId, tid));
        return refs;
    }

    /** 检查经销商引用 */
    public Map<String, Long> dealerReferences(Long dealerId) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Long> refs = new LinkedHashMap<>();
        refs.put("订单",   countByCol("orders", "dealer_id", dealerId, tid));
        refs.put("合同",   countByCol("contracts", "dealer_id", dealerId, tid));
        refs.put("销售出库", countByCol("sales_outs", "dealer_id", dealerId, tid));
        refs.put("授权",   countByCol("authorizations", "dealer_id", dealerId, tid));
        refs.put("库存",   countByCol("inventory", "dealer_id", dealerId, tid));
        refs.put("用户",   countByCol("users", "dealer_id", dealerId, tid));
        return refs;
    }

    /** 检查医院/终端引用 */
    public Map<String, Long> hospitalReferences(Long hospitalId) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Long> refs = new LinkedHashMap<>();
        refs.put("销售出库", countByCol("sales_outs", "terminal_id", hospitalId, tid));
        refs.put("授权",     countByCol("authorizations", "terminal_id", hospitalId, tid));
        return refs;
    }

    /** 检查仓库引用 */
    public Map<String, Long> warehouseReferences(Long warehouseId) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Long> refs = new LinkedHashMap<>();
        refs.put("库存",       countByCol("inventory", "warehouse_id", warehouseId, tid));
        refs.put("收货入库",   countByCol("receipts", "warehouse_id", warehouseId, tid));
        refs.put("调拨(源)",   countByCol("stock_moves", "from_warehouse_id", warehouseId, tid));
        refs.put("调拨(目标)", countByCol("stock_moves", "to_warehouse_id", warehouseId, tid));
        return refs;
    }

    public long totalRefs(Map<String, Long> refs) {
        return refs.values().stream().mapToLong(Long::longValue).sum();
    }

    public String describe(Map<String, Long> refs) {
        StringBuilder sb = new StringBuilder();
        refs.forEach((k, v) -> { if (v > 0) sb.append(k).append("(").append(v).append(") "); });
        return sb.toString().trim();
    }

    private long countByProduct(String table, Long productId, UUID tenantId) {
        return countByCol(table, "product_id", productId, tenantId);
    }

    /**
     * 每次查询独立事务（REQUIRES_NEW），单条失败不影响其他表
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long countByCol(String table, String col, Long id, UUID tenantId) {
        try {
            // 先判断表是否存在
            var tblChk = em.createNativeQuery(
                "SELECT 1 FROM information_schema.tables WHERE table_name = ?1 LIMIT 1");
            tblChk.setParameter(1, table);
            if (tblChk.getResultList().isEmpty()) return 0L;

            // 再判断 tenant_id 列是否存在
            var chk = em.createNativeQuery(
                "SELECT 1 FROM information_schema.columns " +
                "WHERE table_name = ?1 AND column_name = 'tenant_id' LIMIT 1");
            chk.setParameter(1, table);
            boolean hasTenant = !chk.getResultList().isEmpty();

            String sql = hasTenant
                ? "SELECT COUNT(*) FROM " + table + " WHERE " + col + " = ?1 AND tenant_id = ?2"
                : "SELECT COUNT(*) FROM " + table + " WHERE " + col + " = ?1";
            var q = em.createNativeQuery(sql);
            q.setParameter(1, id);
            if (hasTenant) q.setParameter(2, tenantId);
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }
}
