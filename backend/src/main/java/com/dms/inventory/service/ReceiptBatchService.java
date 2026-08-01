/*
 * 收货子单服务 (v3.7.4)
 *
 * 关键业务:
 *   - 创建子单: 生成 code = 父code + "-" + seq(父单内递增)
 *   - 确认收货: 写 inventory (合并/序列号拆分), stock_serials, inventory_transactions
 *              更新 receipt_lines.received_qty, 更新父单 status
 *   - 取消本次: 子单状态 = CANCELLED (未确认过, 不影响库存)
 *   - 取消剩余: 父单 status = COMPLETED 或 CANCELLED (视是否有已确认子单)
 *              将 receipt_lines 中未收部分置为 cancelled_qty += remaining
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptBatchService {

    private final EntityManager em;

    // ============= 创建子单 =============
    @Transactional
    public Map<String, Object> createBatch(Long receiptId) {
        UUID tid = TenantContext.getTenantId();

        var q = em.createNativeQuery(
                "SELECT id, code, status, warehouse_id, source_po_id FROM receipts WHERE id = ?1 AND tenant_id = ?2",
                Tuple.class);
        q.setParameter(1, receiptId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单不存在");
        Tuple r = (Tuple) rs.get(0);
        String rcCode = (String) r.get("code");
        String rcStatus = (String) r.get("status");
        if ("COMPLETED".equals(rcStatus) || "CANCELLED".equals(rcStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单状态 " + rcStatus + " 不允许新建子单");
        }

        Object seqObj = em.createNativeQuery(
                "SELECT COALESCE(MAX(seq), 0) + 1 FROM receipt_batches WHERE receipt_id = ?1")
                .setParameter(1, receiptId).getSingleResult();
        int seq = ((Number) seqObj).intValue();
        String batchCode = rcCode + "-" + seq;

        var ins = em.createNativeQuery(
                "INSERT INTO receipt_batches (tenant_id, receipt_id, code, seq, status, created_at, updated_at, created_by) " +
                "VALUES (?1, ?2, ?3, ?4, 'DRAFT', now(), now(), ?5) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, receiptId).setParameter(3, batchCode)
           .setParameter(4, seq).setParameter(5, TenantContext.getUserId());
        Long batchId = ((Number) ins.getSingleResult()).longValue();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId);
        res.put("receiptId", receiptId);
        res.put("code", batchCode);
        res.put("seq", seq);
        res.put("status", "DRAFT");
        res.put("lines", List.of());
        return res;
    }

    // ============= 更新子单明细 =============
    @Transactional
    public Map<String, Object> updateBatchLines(Long batchId, List<Map<String, Object>> lines) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, receipt_id, status FROM receipt_batches WHERE id = ?1 AND tenant_id = ?2",
                Tuple.class);
        q.setParameter(1, batchId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "子单不存在");
        Tuple b = (Tuple) rs.get(0);
        String status = (String) b.get("status");
        Long __receiptId = ((Number) b.get("receipt_id")).longValue();
        if (!"DRAFT".equals(status)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅 DRAFT 子单可编辑");

        em.createNativeQuery("DELETE FROM receipt_batch_lines WHERE batch_id = ?1")
          .setParameter(1, batchId).executeUpdate();

        int rowIdx = 0;
        for (Map<String, Object> line : lines) {
            rowIdx++;
            Object productId = line.get("productId");
            Object qty = line.get("qty");
            Object batchNo = line.get("batchNo");
            Object serials = line.get("serialNos");
            Object poLineId = line.get("poLineId");
            Object poLineSeq = line.get("poLineSeq");
            Object lineNo = line.getOrDefault("receiptLineNo", rowIdx);
            if (productId == null || qty == null) continue;

            em.createNativeQuery(
                "INSERT INTO receipt_batch_lines (batch_id, po_line_id, po_line_seq, receipt_line_no, product_id, qty, batch_no, serial_nos, created_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, now())")
              .setParameter(1, batchId)
              .setParameter(2, poLineId == null ? null : ((Number) poLineId).longValue())
              .setParameter(3, poLineSeq == null ? null : ((Number) poLineSeq).intValue())
              .setParameter(4, ((Number) lineNo).intValue())
              .setParameter(5, ((Number) productId).longValue())
              .setParameter(6, new BigDecimal(String.valueOf(qty)))
              .setParameter(7, batchNo == null ? null : String.valueOf(batchNo))
              .setParameter(8, serials == null ? null : String.valueOf(serials))
              .executeUpdate();
        }
        em.createNativeQuery("UPDATE receipt_batches SET updated_at = now() WHERE id = ?1")
          .setParameter(1, batchId).executeUpdate();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId);
        res.put("receiptId", __receiptId);
        res.put("linesSaved", lines.size());
        return res;
    }

    // ============= 确认收货 (核心) =============
    @Transactional
    public Map<String, Object> confirmBatch(Long batchId) {
        UUID tid = TenantContext.getTenantId();

        var bq = em.createNativeQuery(
                "SELECT b.id, b.receipt_id, b.status, b.code, r.warehouse_id, r.source_po_id, r.ref_doc_type, r.ref_doc_id, r.status AS r_status, r.is_red " +
                "FROM receipt_batches b JOIN receipts r ON r.id = b.receipt_id " +
                "WHERE b.id = ?1 AND b.tenant_id = ?2", Tuple.class);
        bq.setParameter(1, batchId).setParameter(2, tid);
        List<?> brs = bq.getResultList();
        if (brs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "子单不存在");
        Tuple b = (Tuple) brs.get(0);
        String bStatus = (String) b.get("status");
        if (!"DRAFT".equals(bStatus)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "子单状态 " + bStatus + " 不可确认");
        String rStatus = (String) b.get("r_status");
        if ("CANCELLED".equals(rStatus) || "COMPLETED".equals(rStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "父单已 " + rStatus + ", 不允许再次收货");
        }
        Long receiptId = ((Number) b.get("receipt_id")).longValue();
        Long warehouseId = b.get("warehouse_id") == null ? null : ((Number) b.get("warehouse_id")).longValue();
        Long poId = b.get("source_po_id") == null ? null : ((Number) b.get("source_po_id")).longValue();
        String refDocType = b.get("ref_doc_type") == null ? null : String.valueOf(b.get("ref_doc_type"));
        Long refDocId = b.get("ref_doc_id") == null ? null : ((Number) b.get("ref_doc_id")).longValue();
        if (warehouseId == null) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单未绑定仓库, 无法入库");

        var lq = em.createNativeQuery(
                "SELECT bl.id, bl.po_line_id, bl.product_id, bl.qty, bl.batch_no, bl.serial_nos, " +
                "       p.is_serial_managed, p.name_cn " +
                "FROM receipt_batch_lines bl LEFT JOIN products p ON p.id = bl.product_id " +
                "WHERE bl.batch_id = ?1 ORDER BY bl.id", Tuple.class);
        lq.setParameter(1, batchId);
        @SuppressWarnings("unchecked")
        List<Tuple> lines = lq.getResultList();
        if (lines.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货子单还没有明细，请先添加至少一条收货明细。");

        // 校验
        Map<Long, BigDecimal> qtyByPoLine = new HashMap<>();
        for (Tuple l : lines) {
            Long productId = ((Number) l.get("product_id")).longValue();
            BigDecimal qty = new BigDecimal(String.valueOf(l.get("qty")));
            String batchNo = (String) l.get("batch_no");
            String serialsText = (String) l.get("serial_nos");
            boolean isSerial = Boolean.TRUE.equals(l.get("is_serial_managed"));
            if (qty.signum() <= 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "产品 \"" + l.get("name_cn") + "\" 的本次收货数量必须大于 0。");
            if (batchNo == null || batchNo.isBlank()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "产品 \"" + l.get("name_cn") + "\" 的批次号不能为空，请填写后再确认。");
            if (isSerial) {
                List<String> serials = parseSerials(serialsText);
                if (serials.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "产品 \"" + l.get("name_cn") + "\" 为序列号管理产品，请填写序列号。");
                if (serials.size() != qty.intValue()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "产品 \"" + l.get("name_cn") + "\" 序列号个数 " + serials.size() + " 与本次收货数量 " + qty.intValue() + " 不一致，请修改后再确认。");
            }
            Long poLineId = l.get("po_line_id") == null ? null : ((Number) l.get("po_line_id")).longValue();
            if (poLineId != null) qtyByPoLine.merge(poLineId, qty, BigDecimal::add);
        }

        // 校验总量不超 PO
        for (Map.Entry<Long, BigDecimal> e : qtyByPoLine.entrySet()) {
            Long poLineId = e.getKey();
            BigDecimal delta = e.getValue();
            var poQ = em.createNativeQuery(
                    "SELECT pol.qty, pol.received_qty, p.name_cn " +
                    "FROM purchase_order_lines pol LEFT JOIN products p ON p.id = pol.product_id " +
                    "WHERE pol.id = ?1", Tuple.class);
            poQ.setParameter(1, poLineId);
            List<?> pors = poQ.getResultList();
            if (pors.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "采购订单明细行不存在 id=" + poLineId);
            Tuple po = (Tuple) pors.get(0);
            BigDecimal poQty = new BigDecimal(String.valueOf(po.get("qty")));
            BigDecimal received = new BigDecimal(String.valueOf(po.get("received_qty")));
            BigDecimal after = received.add(delta);
            if (after.compareTo(poQty) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "本次收货数量超过采购订单数量：产品 \"" + po.get("name_cn") + "\" 累计已收 " + received + " + 本次 " + delta + " = " + received.add(delta) + " > 采购数量 " + poQty + "。请减少本次收货量或先取消原有收货。");
            }
        }

        // 写入库
        for (Tuple l : lines) {
            Long productId = ((Number) l.get("product_id")).longValue();
            BigDecimal qty = new BigDecimal(String.valueOf(l.get("qty")));
            String batchNo = (String) l.get("batch_no");
            String serialsText = (String) l.get("serial_nos");
            boolean isSerial = Boolean.TRUE.equals(l.get("is_serial_managed"));
            String stockStatus = "PENDING"; // 待检 (Q)

            if (isSerial) {
                List<String> serials = parseSerials(serialsText);
                for (String sn : serials) {
                    // 每序列号一条 inventory, 不合并
                    em.createNativeQuery(
                        "INSERT INTO inventory (tenant_id, warehouse_id, product_id, batch_no, serial_no, qty, stock_status, in_source, created_at, updated_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, 1, ?6, 'receipt', now(), now()) " +
                        "ON CONFLICT (tenant_id, warehouse_id, product_id, batch_no, serial_no) DO UPDATE SET qty = inventory.qty + 1, updated_at = now()")
                      .setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId)
                      .setParameter(4, batchNo).setParameter(5, sn).setParameter(6, stockStatus)
                      .executeUpdate();
                    em.createNativeQuery(
                        "INSERT INTO stock_serials (tenant_id, warehouse_id, product_id, batch_no, serial_no, stock_status, source_doc_type, source_doc_id, received_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'receipt', ?7, now()) " +
                        "ON CONFLICT (tenant_id, batch_no, serial_no, warehouse_id) DO NOTHING")
                      .setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId)
                      .setParameter(4, batchNo).setParameter(5, sn).setParameter(6, stockStatus)
                      .setParameter(7, receiptId)
                      .executeUpdate();
                }
            } else {
                // 批次管理: 合并到相同 (tenant, warehouse, product, batch_no, status)
                em.createNativeQuery(
                    "INSERT INTO inventory (tenant_id, warehouse_id, product_id, batch_no, serial_no, qty, stock_status, in_source, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, NULL, ?5, ?6, 'receipt', now(), now()) " +
                    "ON CONFLICT (tenant_id, warehouse_id, product_id, batch_no, serial_no) DO UPDATE SET qty = inventory.qty + EXCLUDED.qty, updated_at = now()")
                  .setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId)
                  .setParameter(4, batchNo).setParameter(5, qty).setParameter(6, stockStatus)
                  .executeUpdate();
            }

            // 库存流水
            em.createNativeQuery(
                "INSERT INTO inventory_transactions (tenant_id, warehouse_id, product_id, batch_no, qty_change, txn_type, ref_doc_type, ref_doc_id, at_time, operator_id) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, 'RECEIPT_IN', 'receipt', ?6, now(), ?7)")
              .setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId)
              .setParameter(4, batchNo).setParameter(5, qty).setParameter(6, receiptId).setParameter(7, TenantContext.getUserId())
              .executeUpdate();
        }

        // 更新 PO 明细 received_qty
        for (Map.Entry<Long, BigDecimal> e : qtyByPoLine.entrySet()) {
            em.createNativeQuery(
                "UPDATE purchase_order_lines SET received_qty = received_qty + ?1 WHERE id = ?2")
              .setParameter(1, e.getValue()).setParameter(2, e.getKey())
              .executeUpdate();
        }

        // 更新 receipt_lines.received_qty (按 product 汇总)
        Map<Long, BigDecimal> qtyByProduct = new HashMap<>();
        for (Tuple l : lines) {
            Long pid = ((Number) l.get("product_id")).longValue();
            BigDecimal q = new BigDecimal(String.valueOf(l.get("qty")));
            qtyByProduct.merge(pid, q, BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : qtyByProduct.entrySet()) {
            em.createNativeQuery(
                "UPDATE receipt_lines SET received_qty = received_qty + ?1 WHERE receipt_id = ?2 AND product_id = ?3")
              .setParameter(1, e.getValue()).setParameter(2, receiptId).setParameter(3, e.getKey())
              .executeUpdate();
        }

        // 标记子单 CONFIRMED
        em.createNativeQuery(
            "UPDATE receipt_batches SET status = 'CONFIRMED', confirmed_at = now(), confirmed_by = ?1, updated_at = now() WHERE id = ?2")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, batchId)
          .executeUpdate();

        // 更新父单状态
        String newStatus = recalcReceiptStatus(receiptId);
        em.createNativeQuery("UPDATE receipts SET status = ?1, received_at = now(), updated_at = now() WHERE id = ?2")
          .setParameter(1, newStatus).setParameter(2, receiptId).executeUpdate();

        // 更新 PO 状态: 若全部 received, PO 转 COMPLETED; 若部分, RECEIVING
        if (poId != null) syncPoStatus(poId);

        // v3.8.1 销退入库(RGR)：回写销退订单(orders is_red=true)状态
        if ("sales_return".equals(refDocType) && refDocId != null) {
            syncSalesReturnStatus(refDocId, newStatus);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId);
        res.put("receiptId", receiptId);
        res.put("status", "CONFIRMED");
        res.put("receiptStatus", newStatus);
        return res;
    }

    // ============= 取消本次 =============
    @Transactional
    public Map<String, Object> cancelBatch(Long batchId, String reason) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT status, receipt_id FROM receipt_batches WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, batchId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "子单不存在");
        String status = (String) ((Tuple) rs.get(0)).get("status");
        Long __cancelReceiptId = ((Number) ((Tuple) rs.get(0)).get("receipt_id")).longValue();
        if (!"DRAFT".equals(status)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅 DRAFT 子单可取消, 已确认的请走取消剩余/退货");

        em.createNativeQuery(
            "UPDATE receipt_batches SET status = 'CANCELLED', cancelled_at = now(), cancelled_by = ?1, cancel_reason = ?2, updated_at = now() WHERE id = ?3")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, reason).setParameter(3, batchId)
          .executeUpdate();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId);
        res.put("receiptId", __cancelReceiptId);
        res.put("status", "CANCELLED");
        return res;
    }

    // ============= 取消剩余 =============
    @Transactional
    public Map<String, Object> cancelRemaining(Long receiptId, String reason) {
        UUID tid = TenantContext.getTenantId();
        var rq = em.createNativeQuery(
                "SELECT id, status FROM receipts WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        rq.setParameter(1, receiptId).setParameter(2, tid);
        List<?> rs = rq.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单不存在");
        String status = (String) ((Tuple) rs.get(0)).get("status");
        if ("CANCELLED".equals(status) || "COMPLETED".equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单已终结: " + status);
        }

        // 取消所有 DRAFT 子单
        em.createNativeQuery(
            "UPDATE receipt_batches SET status = 'CANCELLED', cancelled_at = now(), cancelled_by = ?1, cancel_reason = COALESCE(?2, cancel_reason), updated_at = now() " +
            "WHERE receipt_id = ?3 AND status = 'DRAFT'")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, reason).setParameter(3, receiptId)
          .executeUpdate();

        // 更新 receipt_lines: 未收部分 -> cancelled_qty
        em.createNativeQuery(
            "UPDATE receipt_lines SET cancelled_qty = COALESCE(cancelled_qty, 0) + GREATEST(0, COALESCE(expected_qty,0) - COALESCE(received_qty,0) - COALESCE(cancelled_qty,0)), " +
            "                          cancelled_at = now() " +
            "WHERE receipt_id = ?1 AND COALESCE(expected_qty,0) - COALESCE(received_qty,0) - COALESCE(cancelled_qty,0) > 0")
          .setParameter(1, receiptId)
          .executeUpdate();

        // 判断是否有 CONFIRMED 子单
        Object cnt = em.createNativeQuery(
                "SELECT COUNT(*) FROM receipt_batches WHERE receipt_id = ?1 AND status = 'CONFIRMED'")
                .setParameter(1, receiptId).getSingleResult();
        long confirmedCnt = ((Number) cnt).longValue();
        String newStatus = confirmedCnt > 0 ? "COMPLETED" : "CANCELLED";
        em.createNativeQuery("UPDATE receipts SET status = ?1, updated_at = now() WHERE id = ?2")
          .setParameter(1, newStatus).setParameter(2, receiptId).executeUpdate();

        // v3.7.6 R1: 取消剩余后, 同步源采购订单为 COMPLETED (不再可收货)
        var poq = em.createNativeQuery("SELECT source_po_id, ref_doc_type, ref_doc_id FROM receipts WHERE id = ?1")
                    .setParameter(1, receiptId);
        try {
            Tuple r = (Tuple) poq.getSingleResult();
            Object po = r.get("source_po_id");
            if (po != null) {
                Long poId2 = ((Number) po).longValue();
                em.createNativeQuery("UPDATE purchase_orders SET status = ?1, completed_at = now(), updated_at = now() WHERE id = ?2 AND status IN ('APPROVED','RECEIVING')")
                  .setParameter(1, "COMPLETED").setParameter(2, poId2).executeUpdate();
            }
            // v3.8.1 销退入库取消剩余 -> 销退单 COMPLETED
            if ("sales_return".equals(String.valueOf(r.get("ref_doc_type"))) && r.get("ref_doc_id") != null) {
                Long srId = ((Number) r.get("ref_doc_id")).longValue();
                em.createNativeQuery("UPDATE orders SET status='COMPLETED', closed_at=COALESCE(closed_at,now()), updated_at=now() WHERE id=?1 AND COALESCE(is_red,false)=true AND status IN ('APPROVED','RECEIVING')")
                  .setParameter(1, srId).executeUpdate();
            }
        } catch (Exception ignored) {}

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", receiptId);
        res.put("status", newStatus);
        return res;
    }

    // ============= 辅助方法 =============
    private List<String> parseSerials(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] arr = text.split("[\r\n,;\s]+");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            String x = s == null ? "" : s.trim();
            if (!x.isEmpty()) out.add(x);
        }
        return out;
    }

    private String recalcReceiptStatus(Long receiptId) {
        var q = em.createNativeQuery(
                "SELECT COALESCE(SUM(expected_qty),0) AS exp, COALESCE(SUM(received_qty),0) AS rcv, COALESCE(SUM(cancelled_qty),0) AS ccl " +
                "FROM receipt_lines WHERE receipt_id = ?1", Tuple.class);
        q.setParameter(1, receiptId);
        Tuple t = (Tuple) q.getSingleResult();
        BigDecimal exp = new BigDecimal(String.valueOf(t.get("exp")));
        BigDecimal rcv = new BigDecimal(String.valueOf(t.get("rcv")));
        BigDecimal ccl = new BigDecimal(String.valueOf(t.get("ccl")));
        if (rcv.add(ccl).compareTo(exp) >= 0 && exp.signum() > 0) {
            return "COMPLETED";
        }
        if (rcv.signum() > 0) return "PARTIAL_RECEIVED";
        return "DRAFT";
    }

    private void syncPoStatus(Long poId) {
        // v3.7.6 R1: 若源头 PO 对应的所有 receipts 均已终结 (COMPLETED/CANCELLED), PO 转 COMPLETED
        Object openRc = em.createNativeQuery("SELECT COUNT(*) FROM receipts WHERE source_po_id = ?1 AND status NOT IN ('COMPLETED','CANCELLED')").setParameter(1, poId).getSingleResult();
        if (((Number) openRc).longValue() == 0) {
            em.createNativeQuery("UPDATE purchase_orders SET status = 'COMPLETED', completed_at = COALESCE(completed_at, now()), updated_at = now() WHERE id = ?1 AND status IN ('APPROVED','RECEIVING')").setParameter(1, poId).executeUpdate();
            return;
        }
        var q = em.createNativeQuery(
                "SELECT COALESCE(SUM(qty),0) AS tot, COALESCE(SUM(received_qty),0) AS rcv " +
                "FROM purchase_order_lines WHERE po_id = ?1", Tuple.class);
        q.setParameter(1, poId);
        Tuple t = (Tuple) q.getSingleResult();
        BigDecimal tot = new BigDecimal(String.valueOf(t.get("tot")));
        BigDecimal rcv = new BigDecimal(String.valueOf(t.get("rcv")));
        String newStatus;
        if (rcv.compareTo(tot) >= 0 && tot.signum() > 0) newStatus = "COMPLETED";
        else if (rcv.signum() > 0) newStatus = "RECEIVING";
        else return; // 未变
        em.createNativeQuery("UPDATE purchase_orders SET status = ?1, updated_at = now() WHERE id = ?2 AND status IN ('APPROVED','RECEIVING')")
          .setParameter(1, newStatus).setParameter(2, poId).executeUpdate();
    }

    private void syncSalesReturnStatus(Long orderId, String receiptStatus) {
        try {
            // 以销退单关联的 RGR 入库单整体进度判断
            var q = em.createNativeQuery(
                    "SELECT COALESCE(SUM(rl.expected_qty),0) AS exp, COALESCE(SUM(rl.received_qty),0) AS rcv, COALESCE(SUM(rl.cancelled_qty),0) AS ccl " +
                    "FROM receipt_lines rl JOIN receipts r ON r.id=rl.receipt_id " +
                    "WHERE r.ref_doc_type='sales_return' AND r.ref_doc_id=?1", Tuple.class);
            q.setParameter(1, orderId);
            Tuple t = (Tuple) q.getSingleResult();
            BigDecimal exp = new BigDecimal(String.valueOf(t.get("exp")));
            BigDecimal rcv = new BigDecimal(String.valueOf(t.get("rcv")));
            BigDecimal ccl = new BigDecimal(String.valueOf(t.get("ccl")));
            String orderStatus;
            if (exp.signum() > 0 && rcv.add(ccl).compareTo(exp) >= 0) orderStatus = "COMPLETED";
            else if (rcv.signum() > 0) orderStatus = "RECEIVING";
            else orderStatus = "APPROVED";
            em.createNativeQuery(
                    "UPDATE orders SET status=?1, received_at=COALESCE(received_at, CASE WHEN ?1='RECEIVING' THEN now() ELSE received_at END), " +
                    "closed_at=CASE WHEN ?1='COMPLETED' THEN COALESCE(closed_at,now()) ELSE closed_at END, updated_at=now() " +
                    "WHERE id=?2 AND COALESCE(is_red,false)=true AND status IN ('APPROVED','RECEIVING','COMPLETED')")
              .setParameter(1, orderStatus).setParameter(2, orderId).executeUpdate();
        } catch (Exception e) {
            // ignore
        }
    }
}