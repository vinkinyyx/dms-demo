/*
 * 自动生成出/入库单服务 (v3.4)
 *   订单审批通过 → 生成销售出库草稿单
 *   采购单审批通过 → 生成采购入库草稿单
 */
package com.dms.execution.service;

import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoDocGenerator {

    private final EntityManager em;
    private final com.dms.common.util.DocNoGenerator docNoGenerator;

    /**
     * 订单审批通过后自动生成销售出库草稿
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long createSalesOutForOrder(Long orderId) {
        UUID tid = TenantContext.getTenantId();

        var q = em.createNativeQuery(
                "SELECT id, is_red, dealer_id, ship_snapshot FROM orders WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, orderId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new RuntimeException("订单不存在: " + orderId);
        Tuple t = (Tuple) rs.get(0);
        boolean isRed = Boolean.TRUE.equals(t.get("is_red"));
        Long dealerId = t.get("dealer_id") == null ? null : ((Number) t.get("dealer_id")).longValue();

        // 检查是否已存在该订单对应的销售出库单
        var chk = em.createNativeQuery("SELECT id FROM sales_outs WHERE source_order_id = ?1 AND tenant_id = ?2");
        chk.setParameter(1, orderId).setParameter(2, tid);
        List<?> exists = chk.getResultList();
        if (!exists.isEmpty()) {
            log.info("订单 {} 已有对应销售出库单，跳过自动创建", orderId);
            return ((Number) exists.get(0)).longValue();
        }

        String code = docNoGenerator.next(isRed ? "RCK" : "CK");
        var ins = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id, code, dealer_id, is_red, status, auto_created, source_order_id, sales_date, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, 'DRAFT', true, ?5, now(), now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, dealerId)
                .setParameter(4, isRed).setParameter(5, orderId);
        Long soId = ((Number) ins.getSingleResult()).longValue();

        // 拷贝明细
        var lq = em.createNativeQuery(
                "SELECT product_id, qty FROM order_lines WHERE order_id = ?1", Tuple.class);
        lq.setParameter(1, orderId);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        // 取仓库：从订单表或者默认拿一个
        Long defaultWh = null;
        try {
            var whQ = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id = ?1 LIMIT 1");
            whQ.setParameter(1, tid);
            Object o = whQ.getSingleResult();
            if (o != null) defaultWh = ((Number) o).longValue();
        } catch (Exception ignored) {}
        for (Tuple l : ls) {
            try {
                em.createNativeQuery(
                        "INSERT INTO sales_out_lines (sales_out_id, product_id, warehouse_id, qty) " +
                        "VALUES (?1, ?2, ?3, ?4)")
                    .setParameter(1, soId).setParameter(2, l.get("product_id"))
                    .setParameter(3, defaultWh)
                    .setParameter(4, l.get("qty")).executeUpdate();
            } catch (Exception ex) {
                log.warn("拷贝订单 {} 明细失败: {}", orderId, ex.getMessage());
            }
        }

        // 更新出库单主表的仓库(取明细第一行的)
        try {
            em.createNativeQuery(
                    "UPDATE sales_outs SET warehouse_id = (SELECT warehouse_id FROM sales_out_lines WHERE sales_out_id = ?1 LIMIT 1) WHERE id = ?1")
                .setParameter(1, soId).executeUpdate();
        } catch (Exception ignored) {}

        log.info("已为订单 {} 创建销售出库草稿单 {} (id={})", orderId, code, soId);
        return soId;
    }

    /**
     * 采购单审批通过后自动生成采购入库草稿
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long createReceiptForPurchaseOrder(Long poId) {
        UUID tid = TenantContext.getTenantId();

        var q = em.createNativeQuery(
                "SELECT id, is_red, supplier_id, warehouse_id FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2",
                Tuple.class);
        q.setParameter(1, poId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new RuntimeException("采购单不存在: " + poId);
        Tuple t = (Tuple) rs.get(0);
        boolean isRed = Boolean.TRUE.equals(t.get("is_red"));
        Long whId = t.get("warehouse_id") == null ? null : ((Number) t.get("warehouse_id")).longValue();
        // v3.4.11: 采购单未选仓库时用默认仓库兜底，避免 RK 仓库为空导致后续收货报错
        if (whId == null) {
            try {
                var whQ = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id = ?1 ORDER BY id LIMIT 1");
                whQ.setParameter(1, tid);
                Object o = whQ.getSingleResult();
                if (o != null) whId = ((Number) o).longValue();
            } catch (Exception ignored) {}
        }

        var chk = em.createNativeQuery("SELECT id FROM receipts WHERE source_po_id = ?1 AND tenant_id = ?2");
        chk.setParameter(1, poId).setParameter(2, tid);
        List<?> exists = chk.getResultList();
        if (!exists.isEmpty()) {
            log.info("采购单 {} 已有对应入库单，跳过自动创建", poId);
            return ((Number) exists.get(0)).longValue();
        }

        String code = docNoGenerator.next(isRed ? "RRK" : "RK");
        var ins = em.createNativeQuery(
                "INSERT INTO receipts (tenant_id, code, ref_doc_type, ref_doc_id, is_red, status, auto_created, source_po_id, warehouse_id, created_at, updated_at) " +
                "VALUES (?1, ?2, 'purchase_order', ?3, ?4, 'DRAFT', true, ?3, ?5, now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, poId)
                .setParameter(4, isRed).setParameter(5, whId);
        Long rcId = ((Number) ins.getSingleResult()).longValue();

        var lq = em.createNativeQuery(
                "SELECT product_id, qty FROM purchase_order_lines WHERE po_id = ?1", Tuple.class);
        lq.setParameter(1, poId);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        for (Tuple l : ls) {
            try {
                em.createNativeQuery(
                        "INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty) " +
                        "VALUES (?1, ?2, ?3, 0)")
                    .setParameter(1, rcId).setParameter(2, l.get("product_id"))
                    .setParameter(3, l.get("qty")).executeUpdate();
            } catch (Exception ex) {
                log.warn("拷贝采购单 {} 明细失败: {}", poId, ex.getMessage());
            }
        }

        log.info("已为采购单 {} 创建入库草稿单 {} (id={})", poId, code, rcId);
        return rcId;
    }
}
