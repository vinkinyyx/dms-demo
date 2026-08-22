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
                "SELECT id, is_red, dealer_id, warehouse_id, amount_incl_tax FROM orders WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, orderId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new RuntimeException("订单不存在: " + orderId);
        Tuple t = (Tuple) rs.get(0);
        boolean isRed = Boolean.TRUE.equals(t.get("is_red"));
        Long dealerId = t.get("dealer_id") == null ? null : ((Number) t.get("dealer_id")).longValue();
        Long whId = t.get("warehouse_id") == null ? null : ((Number) t.get("warehouse_id")).longValue();

        var chk = em.createNativeQuery(
                "SELECT id FROM sales_outs WHERE source_order_id = ?1 AND tenant_id = ?2 AND COALESCE(is_red,false) = ?3 AND deleted_at IS NULL " +
                "ORDER BY id DESC LIMIT 1");
        chk.setParameter(1, orderId).setParameter(2, tid).setParameter(3, isRed);
        @SuppressWarnings("unchecked")
        List<?> exists = chk.getResultList();
        if (!exists.isEmpty()) {
            log.info("订单 {} 已有对应销售出库单，跳过自动创建", orderId);
            return ((Number) exists.get(0)).longValue();
        }

        // 仓库兜底
        if (whId == null) {
            try {
                var whQ = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id = ?1 ORDER BY id LIMIT 1");
                whQ.setParameter(1, tid);
                Object o = whQ.getSingleResult();
                if (o != null) whId = ((Number) o).longValue();
            } catch (Exception ignored) {}
        }

        String code = docNoGenerator.next(isRed ? "GIR" : "GI");
        var ins = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id, code, dealer_id, warehouse_id, is_red, status, auto_created, source_order_id, " +
                "amount_incl_tax, sales_date, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, 'DRAFT', true, ?6, ?7, now(), now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, dealerId).setParameter(4, whId)
           .setParameter(5, isRed).setParameter(6, orderId).setParameter(7, t.get("amount_incl_tax"));
        Long soId = ((Number) ins.getSingleResult()).longValue();

        // 拷贝明细：把订单行的 qty 作为 expected_qty（应发数），shipped_qty/qty 初始化为 0。
        // v4.1.1：跳过 BOM 母件行（无实物）和促销赠品行（0 元，不可出库/退货），并记录源订单行 id。
        var lq = em.createNativeQuery(
                "SELECT id, product_id, qty, unit_price, tax_rate, sub_total, seq, line_level, is_gift FROM order_lines WHERE order_id = ?1 ORDER BY seq, id", Tuple.class);
        lq.setParameter(1, orderId);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        int seq = 1;
        for (Tuple l : ls) {
            try {
                String lineLevel = l.get("line_level") == null ? "NORMAL" : String.valueOf(l.get("line_level"));
                boolean isGift = Boolean.TRUE.equals(l.get("is_gift"));
                if ("PARENT".equals(lineLevel)) continue;
                em.createNativeQuery(
                        "INSERT INTO sales_out_lines (sales_out_id, seq, product_id, warehouse_id, source_order_line_id, expected_qty, shipped_qty, qty, " +
                        "unit_price, tax_rate, subtotal, created_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, 0, 0, ?7, ?8, ?9, now())")
                    .setParameter(1, soId).setParameter(2, seq++)
                    .setParameter(3, l.get("product_id")).setParameter(4, whId).setParameter(5, l.get("id"))
                    .setParameter(6, l.get("qty"))
                    .setParameter(7, l.get("unit_price"))
                    .setParameter(8, l.get("tax_rate"))
                    .setParameter(9, l.get("sub_total"))
                    .executeUpdate();
            } catch (Exception ex) {
                log.warn("拷贝订单 {} 明细失败: {}", orderId, ex.getMessage());
            }
        }

        log.info("已为订单 {} 创建销售出库草稿单 {} (id={}, wh={})", orderId, code, soId, whId);
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

        String code = docNoGenerator.next(isRed ? "GRR" : "GR");
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

    /**
     * v3.8.1 销退订单审批通过后自动生成销退入库草稿（RGR）。
     * 销退：客户退货入我方仓库，库存增加，入待检(PENDING)。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long createReceiptForSalesReturn(Long orderId) {
        UUID tid = TenantContext.getTenantId();

        var q = em.createNativeQuery(
                "SELECT id, dealer_id, warehouse_id, amount_incl_tax FROM orders WHERE id = ?1 AND tenant_id = ?2 AND is_red = true", Tuple.class);
        q.setParameter(1, orderId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new RuntimeException("销退订单不存在或非红字单: " + orderId);
        Tuple t = (Tuple) rs.get(0);
        Long whId = t.get("warehouse_id") == null ? null : ((Number) t.get("warehouse_id")).longValue();
        Long dealerId = t.get("dealer_id") == null ? null : ((Number) t.get("dealer_id")).longValue();
        if (whId == null) {
            try {
                var whQ = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id = ?1 ORDER BY id LIMIT 1");
                whQ.setParameter(1, tid);
                Object o = whQ.getSingleResult();
                if (o != null) whId = ((Number) o).longValue();
            } catch (Exception ignored) {}
        }

        var chk = em.createNativeQuery(
                "SELECT id FROM receipts WHERE ref_doc_type = 'sales_return' AND ref_doc_id = ?1 AND tenant_id = ?2 AND COALESCE(is_red,false) = true AND deleted_at IS NULL " +
                "ORDER BY id DESC LIMIT 1");
        chk.setParameter(1, orderId).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<?> exists = chk.getResultList();
        if (!exists.isEmpty()) {
            log.info("销退订单 {} 已有对应入库单，跳过自动创建", orderId);
            return ((Number) exists.get(0)).longValue();
        }

        String code = docNoGenerator.next("RGR");
        var ins = em.createNativeQuery(
                "INSERT INTO receipts (tenant_id, code, ref_doc_type, ref_doc_id, dealer_id, warehouse_id, is_red, status, auto_created, created_at, updated_at) " +
                "VALUES (?1, ?2, 'sales_return', ?3, ?4, ?5, true, 'DRAFT', true, now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, orderId)
           .setParameter(4, dealerId).setParameter(5, whId);
        Long rcId = ((Number) ins.getSingleResult()).longValue();

        var lq = em.createNativeQuery(
                "SELECT product_id, qty, unit_price, tax_rate, sub_total, seq FROM order_lines WHERE order_id = ?1 ORDER BY seq, id", Tuple.class);
        lq.setParameter(1, orderId);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        for (Tuple l : ls) {
            try {
                em.createNativeQuery(
                        "INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty, created_at) " +
                        "VALUES (?1, ?2, ?3, 0, now())")
                    .setParameter(1, rcId)
                    .setParameter(2, l.get("product_id"))
                    .setParameter(3, l.get("qty"))
                    .executeUpdate();
            } catch (Exception ex) {
                log.warn("拷贝销退订单 {} 明细失败: {}", orderId, ex.getMessage());
            }
        }

        log.info("已为销退订单 {} 创建入库草稿单 {} (id={}, wh={})", orderId, code, rcId, whId);
        return rcId;
    }

    /**
     * v3.8.1 采退订单审批通过后自动生成采退出库草稿（RGI）。
     * 采退：我方退货给供应商，库存减少，不限制库存状态。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long createSalesOutForPurchaseReturn(Long poId) {
        UUID tid = TenantContext.getTenantId();

        var q = em.createNativeQuery(
                "SELECT id, supplier_id, warehouse_id, amount_incl_tax FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2 AND is_red = true", Tuple.class);
        q.setParameter(1, poId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new RuntimeException("采退订单不存在或非红字单: " + poId);
        Tuple t = (Tuple) rs.get(0);
        Long whId = t.get("warehouse_id") == null ? null : ((Number) t.get("warehouse_id")).longValue();
        if (whId == null) {
            try {
                var whQ = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id = ?1 ORDER BY id LIMIT 1");
                whQ.setParameter(1, tid);
                Object o = whQ.getSingleResult();
                if (o != null) whId = ((Number) o).longValue();
            } catch (Exception ignored) {}
        }

        var chk = em.createNativeQuery(
                "SELECT id FROM sales_outs WHERE source_po_id = ?1 AND tenant_id = ?2 AND COALESCE(is_red,false) = true AND deleted_at IS NULL " +
                "ORDER BY id DESC LIMIT 1");
        chk.setParameter(1, poId).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<?> exists = chk.getResultList();
        if (!exists.isEmpty()) {
            log.info("采退订单 {} 已有对应出库单，跳过自动创建", poId);
            return ((Number) exists.get(0)).longValue();
        }

        String code = docNoGenerator.next("RGI");
        var ins = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id, code, warehouse_id, is_red, status, auto_created, source_po_id, " +
                "amount_incl_tax, sales_date, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, true, 'DRAFT', true, ?4, ?5, now(), now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, whId)
           .setParameter(4, poId).setParameter(5, t.get("amount_incl_tax"));
        Long soId = ((Number) ins.getSingleResult()).longValue();

        var lq = em.createNativeQuery(
                "SELECT product_id, qty, unit_price, tax_rate, subtotal, seq FROM purchase_order_lines WHERE po_id = ?1 ORDER BY seq, id", Tuple.class);
        lq.setParameter(1, poId);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        int seq = 1;
        for (Tuple l : ls) {
            try {
                em.createNativeQuery(
                        "INSERT INTO sales_out_lines (sales_out_id, seq, product_id, warehouse_id, expected_qty, shipped_qty, qty, " +
                        "unit_price, tax_rate, subtotal, created_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, 0, 0, ?6, ?7, ?8, now())")
                    .setParameter(1, soId).setParameter(2, seq++)
                    .setParameter(3, l.get("product_id")).setParameter(4, whId)
                    .setParameter(5, l.get("qty"))
                    .setParameter(6, l.get("unit_price"))
                    .setParameter(7, l.get("tax_rate"))
                      .setParameter(8, l.get("subtotal"))
                    .executeUpdate();
            } catch (Exception ex) {
                log.warn("拷贝采退订单 {} 明细失败: {}", poId, ex.getMessage());
            }
        }

        log.info("已为采退订单 {} 创建出库草稿单 {} (id={}, wh={})", poId, code, soId, whId);
        return soId;
    }
}

