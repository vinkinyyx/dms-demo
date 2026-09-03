package com.dms.sales.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Sales-out sub-document (shipment batch) service, mirroring receipt_batches.
 * Key difference from receipt: batch_no/serial_no must be selected from on-hand QUALIFIED stock.
 */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class SalesOutBatchService {

    private final EntityManager em;
    private final com.dms.collab.CrossTenantCollabService crossTenantCollab;
    @org.springframework.context.annotation.Lazy
    private final com.dms.openapi.service.ExternalCollabWebhookService externalCollabWebhook;

    @Transactional
    public Map<String, Object> createBatch(Long salesOutId) {
        UUID tid = TenantContext.getTenantId();
        Tuple r = loadOut(tid, salesOutId);
        String soStatus = (String) r.get("status");
        if ("COMPLETED".equals(soStatus) || "CANCELLED".equals(soStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Out doc status " + soStatus + " does not allow new shipment");
        }
        if (r.get("warehouse_id") == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Out doc has no warehouse, cannot ship");
        }
        Object maxSeq = em.createNativeQuery(
                "SELECT COALESCE(MAX(seq),0) FROM sales_out_batches WHERE sales_out_id = ?1")
                .setParameter(1, salesOutId).getSingleResult();
        int seq = ((Number) maxSeq).intValue() + 1;
        String code = r.get("code") + "-" + seq;

        Object bid = em.createNativeQuery(
                "INSERT INTO sales_out_batches (tenant_id, sales_out_id, code, seq, status, created_at, updated_at, created_by) " +
                "VALUES (?1, ?2, ?3, ?4, 'DRAFT', now(), now(), ?5) RETURNING id")
          .setParameter(1, tid).setParameter(2, salesOutId).setParameter(3, code)
          .setParameter(4, seq).setParameter(5, TenantContext.getUserId())
          .getSingleResult();
        Long batchId = ((Number) bid).longValue();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId);
        res.put("salesOutId", salesOutId);
        res.put("code", code);
        res.put("seq", seq);
        res.put("status", "DRAFT");
        log.info("Sales-out {} created shipment batch {}", salesOutId, code);
        return res;
    }

    @Transactional
    public Map<String, Object> updateBatchLines(Long batchId, List<Map<String, Object>> lines) {
        UUID tid = TenantContext.getTenantId();
        Tuple b = loadBatch(tid, batchId);
        String status = (String) b.get("status");
        Long soId = ((Number) b.get("sales_out_id")).longValue();
        if (!"DRAFT".equals(status)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only DRAFT batch is editable");

        validateLines(tid, soId, lines);

        em.createNativeQuery("DELETE FROM sales_out_batch_lines WHERE batch_id = ?1")
          .setParameter(1, batchId).executeUpdate();

        int rowIdx = 0;
        for (Map<String, Object> line : lines) {
            rowIdx++;
            Object productId = line.get("productId");
            Object qty = line.get("qty");
            if (productId == null || qty == null) continue;
            Object lineNo = line.getOrDefault("shipLineNo", rowIdx);
            em.createNativeQuery(
                "INSERT INTO sales_out_batch_lines (batch_id, expected_line_id, expected_line_seq, ship_line_no, product_id, warehouse_id, qty, stock_batch_id, batch_no, serial_no, unit_price, created_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, now())")
              .setParameter(1, batchId)
              .setParameter(2, num(line.get("expectedLineId")))
              .setParameter(3, intVal(line.get("expectedLineSeq")))
              .setParameter(4, ((Number) lineNo).intValue())
              .setParameter(5, ((Number) productId).longValue())
              .setParameter(6, num(line.get("warehouseId")))
              .setParameter(7, new BigDecimal(String.valueOf(qty)))
              .setParameter(8, num(line.get("stockBatchId")))
              .setParameter(9, line.get("batchNo") == null ? null : String.valueOf(line.get("batchNo")))
              .setParameter(10, (line.get("serialNo") == null || String.valueOf(line.get("serialNo")).isBlank()) ? null : String.valueOf(line.get("serialNo")))
              .setParameter(11, line.get("unitPrice") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(line.get("unitPrice"))))
              .executeUpdate();
        }
        em.createNativeQuery("UPDATE sales_out_batches SET updated_at = now() WHERE id = ?1")
          .setParameter(1, batchId).executeUpdate();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId);
        res.put("salesOutId", soId);
        res.put("linesSaved", lines.size());
        return res;
    }
    @Transactional
    public Map<String, Object> confirmBatch(Long batchId) {
        UUID tid = TenantContext.getTenantId();
        var bq = em.createNativeQuery(
                "SELECT b.id, b.sales_out_id, b.status, so.warehouse_id, so.dealer_id, so.source_order_id, so.source_po_id, so.status AS so_status, COALESCE(so.is_red,false) AS is_red " +
                "FROM sales_out_batches b JOIN sales_outs so ON so.id = b.sales_out_id " +
                "WHERE b.id = ?1 AND b.tenant_id = ?2", Tuple.class);
        bq.setParameter(1, batchId).setParameter(2, tid);
        List<?> brs = bq.getResultList();
        if (brs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Batch not found");
        Tuple b = (Tuple) brs.get(0);
        if (!"DRAFT".equals(b.get("status"))) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Batch is not DRAFT");
        String soStatus = (String) b.get("so_status");
        if ("CANCELLED".equals(soStatus) || "COMPLETED".equals(soStatus))
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Out doc is " + soStatus + ", cannot ship");

        Long soId = ((Number) b.get("sales_out_id")).longValue();
        Long warehouseId = b.get("warehouse_id") == null ? null : ((Number) b.get("warehouse_id")).longValue();
        Long orderId = b.get("source_order_id") == null ? null : ((Number) b.get("source_order_id")).longValue();
        Long poId = b.get("source_po_id") == null ? null : ((Number) b.get("source_po_id")).longValue();
        boolean isRed = Boolean.TRUE.equals(b.get("is_red"));
        if (warehouseId == null) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Out doc has no warehouse");

        var lq = em.createNativeQuery(
                "SELECT bl.id, bl.expected_line_id, bl.product_id, bl.warehouse_id, bl.qty, bl.stock_batch_id, bl.batch_no, bl.serial_no, bl.unit_price, " +
                "       p.is_serial_managed, p.code AS product_code " +
                "FROM sales_out_batch_lines bl LEFT JOIN products p ON p.id = bl.product_id " +
                "WHERE bl.batch_id = ?1 ORDER BY bl.ship_line_no, bl.id", Tuple.class);
        lq.setParameter(1, batchId);
        @SuppressWarnings("unchecked")
        List<Tuple> lines = lq.getResultList();
        if (lines.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "Batch has no lines, please save first");

        validateLoadedLines(tid, soId, lines, isRed);

        Long userId = TenantContext.getUserId();
        java.util.List<com.dms.collab.ShippedLine> collabRedLines = new java.util.ArrayList<>();
        java.util.List<com.dms.collab.ShippedLine> webhookLines = new java.util.ArrayList<>();
        for (Tuple l : lines) {
            Long productId = ((Number) l.get("product_id")).longValue();
            Long lineWh = l.get("warehouse_id") == null ? warehouseId : ((Number) l.get("warehouse_id")).longValue();
            BigDecimal qty = new BigDecimal(String.valueOf(l.get("qty")));
            String batchNo = l.get("batch_no") == null ? null : String.valueOf(l.get("batch_no"));
            String serialNo = l.get("serial_no") == null ? null : String.valueOf(l.get("serial_no"));
            BigDecimal unitPrice = l.get("unit_price") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(l.get("unit_price")));
            boolean isSerial = Boolean.TRUE.equals(l.get("is_serial_managed"));

            if (isSerial) deductSerial(tid, lineWh, productId, batchNo, serialNo, isRed);
            else deductBatch(tid, lineWh, productId, batchNo, qty, isRed);

            em.createNativeQuery(
                "INSERT INTO inventory_transactions (tenant_id, warehouse_id, product_id, batch_no, serial_no, qty_change, txn_type, ref_doc_type, ref_doc_id, source_line_id, at_time, operator_id) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'SALES_OUT', 'sales_out', ?7, ?8, now(), ?9)")
              .setParameter(1, tid).setParameter(2, lineWh).setParameter(3, productId).setParameter(4, batchNo)
              .setParameter(5, serialNo).setParameter(6, qty.negate()).setParameter(7, soId)
              .setParameter(8, ((Number) l.get("id")).longValue()).setParameter(9, userId)
              .executeUpdate();

            Object newSolRow = em.createNativeQuery(
                "INSERT INTO sales_out_lines (sales_out_id, seq, product_id, warehouse_id, stock_batch_id, batch_no, serial_no, " +
                "  qty, quantity, shipped_qty, expected_qty, unit_price, subtotal, is_red, created_at) " +
                "VALUES (?1, COALESCE((SELECT MAX(seq) FROM sales_out_lines s2 WHERE s2.sales_out_id = ?1),0)+1, " +
                "  ?2, ?3, ?4, ?5, ?6, CAST(?7 AS numeric), CAST(?7 AS numeric), CAST(?7 AS numeric), 0, CAST(?8 AS numeric), CAST(?7 AS numeric) * CAST(?8 AS numeric), ?9, now()) RETURNING id")
              .setParameter(1, soId).setParameter(2, productId).setParameter(3, lineWh)
              .setParameter(4, l.get("stock_batch_id") == null ? null : ((Number) l.get("stock_batch_id")).longValue())
              .setParameter(5, batchNo).setParameter(6, serialNo).setParameter(7, qty).setParameter(8, unitPrice)
              .setParameter(9, isRed)
              .getSingleResult();
            Long newSalesOutLineId = ((Number) newSolRow).longValue();

            com.dms.collab.ShippedLine wl = new com.dms.collab.ShippedLine();
            wl.setProductId(productId);
            wl.setProductCode(l.get("product_code") == null ? null : String.valueOf(l.get("product_code")));
            wl.setQty(qty);
            wl.setBatchNo(batchNo);
            wl.setSerialNo(serialNo);
            wl.setOutLineId(newSalesOutLineId);
            webhookLines.add(wl);

            if (isRed) {
                com.dms.collab.ShippedLine sl = new com.dms.collab.ShippedLine();
                sl.setProductId(productId);
                sl.setProductCode(wl.getProductCode());
                sl.setQty(qty);
                sl.setBatchNo(batchNo);
                sl.setSerialNo(serialNo);
                sl.setOutLineId(((Number) l.get("id")).longValue());
                collabRedLines.add(sl);
            }
        }

        Map<Long, BigDecimal> addByExpLine = new HashMap<>();
        for (Tuple l : lines) {
            if (l.get("expected_line_id") == null) continue;
            Long expId = ((Number) l.get("expected_line_id")).longValue();
            addByExpLine.merge(expId, new BigDecimal(String.valueOf(l.get("qty"))), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : addByExpLine.entrySet()) {
            em.createNativeQuery(
                "UPDATE sales_out_lines SET shipped_qty = COALESCE(shipped_qty,0) + ?1, qty = COALESCE(shipped_qty,0) + ?1 WHERE id = ?2")
              .setParameter(1, e.getValue()).setParameter(2, e.getKey()).executeUpdate();
        }

        em.createNativeQuery(
            "UPDATE sales_out_batches SET status = 'CONFIRMED', confirmed_at = now(), confirmed_by = ?1, updated_at = now() WHERE id = ?2")
          .setParameter(1, userId).setParameter(2, batchId).executeUpdate();

        String newStatus = recalcSalesOutStatus(soId);
        em.createNativeQuery(
            "UPDATE sales_outs SET status = ?1, shipped_at = COALESCE(shipped_at, now()), " +
            "  completed_at = CASE WHEN ?1 = 'COMPLETED' THEN now() ELSE completed_at END, updated_at = now() WHERE id = ?2")
          .setParameter(1, newStatus).setParameter(2, soId).executeUpdate();

        if (orderId != null) syncOrderStatus(orderId, newStatus);
        if (poId != null) syncPurchaseReturnStatus(poId, newStatus);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId);
        res.put("salesOutId", soId);
        res.put("status", "CONFIRMED");
        res.put("salesOutStatus", newStatus);
        log.info("Sales-out batch {} confirmed, parent status={}", batchId, newStatus);

        // v4.5.4 跨租户反向：经销商红字销售出库（采退发货）-> 厂家红字销退入库待收货（对码缺失随发货事务回滚）
        if (isRed && !collabRedLines.isEmpty()) {
            try {
                crossTenantCollab.onRedSalesOutShipped(soId, collabRedLines);
            } catch (com.dms.common.BusinessException be) {
                throw be;
            } catch (Exception ce) {
                log.error("跨租户红字出库回传失败 soId={}", soId, ce);
                throw ce;
            }
        }

        // v4.5.4 平台外经销商：发货报文 webhook 回传（正常+红字批次发货均触发；事务提交后异步推送，失败仅落台账重试，不阻断发货）
        if (!webhookLines.isEmpty()) {
            try {
                externalCollabWebhook.registerOutbound(soId, webhookLines, isRed);
            } catch (Exception we) {
                log.warn("外部经销商发货回传登记失败 batchId={}: {}", batchId, we.getMessage());
            }
        }
        return res;
    }

    @Transactional
    public Map<String, Object> cancelBatch(Long batchId, String reason) {
        UUID tid = TenantContext.getTenantId();
        Tuple b = loadBatch(tid, batchId);
        if (!"DRAFT".equals(b.get("status")))
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only DRAFT batch can be cancelled");
        Long soId = ((Number) b.get("sales_out_id")).longValue();
        em.createNativeQuery(
            "UPDATE sales_out_batches SET status = 'CANCELLED', cancelled_at = now(), cancelled_by = ?1, cancel_reason = ?2, updated_at = now() WHERE id = ?3")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, reason).setParameter(3, batchId)
          .executeUpdate();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", batchId); res.put("salesOutId", soId); res.put("status", "CANCELLED");
        return res;
    }

    @Transactional
    public Map<String, Object> cancelRemaining(Long salesOutId, String reason) {
        UUID tid = TenantContext.getTenantId();
        Tuple r = loadOut(tid, salesOutId);
        String status = (String) r.get("status");
        if ("CANCELLED".equals(status) || "COMPLETED".equals(status))
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Out doc already closed: " + status);
        Long orderId = r.get("source_order_id") == null ? null : ((Number) r.get("source_order_id")).longValue();

        em.createNativeQuery(
            "UPDATE sales_out_batches SET status = 'CANCELLED', cancelled_at = now(), cancelled_by = ?1, cancel_reason = ?2, updated_at = now() " +
            "WHERE sales_out_id = ?3 AND status = 'DRAFT'")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, reason).setParameter(3, salesOutId)
          .executeUpdate();

        em.createNativeQuery(
            "UPDATE sales_out_lines SET cancelled_qty = COALESCE(cancelled_qty,0) + GREATEST(0, COALESCE(expected_qty,0) - COALESCE(shipped_qty,0) - COALESCE(cancelled_qty,0)), cancelled_at = now() " +
            "WHERE sales_out_id = ?1 AND COALESCE(expected_qty,0) - COALESCE(shipped_qty,0) - COALESCE(cancelled_qty,0) > 0")
          .setParameter(1, salesOutId).executeUpdate();

        long confirmedCnt = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sales_out_batches WHERE sales_out_id = ?1 AND status = 'CONFIRMED'")
                .setParameter(1, salesOutId).getSingleResult()).longValue();
        String newStatus = confirmedCnt > 0 ? "COMPLETED" : "CANCELLED";
        em.createNativeQuery(
            "UPDATE sales_outs SET status = ?1, completed_at = CASE WHEN ?1 = 'COMPLETED' THEN now() ELSE completed_at END, " +
            "  cancelled_at = CASE WHEN ?1 = 'CANCELLED' THEN now() ELSE cancelled_at END, updated_at = now() WHERE id = ?2")
          .setParameter(1, newStatus).setParameter(2, salesOutId).executeUpdate();

        if (orderId != null) {
            safeUpdateOrder(orderId, "COMPLETED".equals(newStatus) ? "COMPLETED" : "CANCELLED");
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", salesOutId); res.put("status", newStatus);
        return res;
    }

    private void validateLines(UUID tid, Long soId, List<Map<String, Object>> lines) {
        if (lines == null || lines.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "At least one line is required");
        Map<Long, BigDecimal> remaining = new HashMap<>();
        var eq = em.createNativeQuery(
                "SELECT id, COALESCE(expected_qty,0) - COALESCE(shipped_qty,0) - COALESCE(cancelled_qty,0) AS rem " +
                "FROM sales_out_lines WHERE sales_out_id = ?1 AND COALESCE(expected_qty,0) > 0", Tuple.class);
        eq.setParameter(1, soId);
        for (Object o : eq.getResultList()) {
            Tuple t = (Tuple) o;
            remaining.put(((Number) t.get("id")).longValue(), new BigDecimal(String.valueOf(t.get("rem"))));
        }
        Map<Long, BigDecimal> reqByLine = new HashMap<>();
        int rowNo = 0;
        for (Map<String, Object> line : lines) {
            rowNo++;
            Object productId = line.get("productId");
            Object qty = line.get("qty");
            if (productId == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "Line " + rowNo + ": product is required");
            if (qty == null || new BigDecimal(String.valueOf(qty)).signum() <= 0)
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Line " + rowNo + ": qty must be positive");
            if (line.get("expectedLineId") != null) {
                Long id = ((Number) line.get("expectedLineId")).longValue();
                BigDecimal rem = remaining.getOrDefault(id, BigDecimal.ZERO);
                BigDecimal add = reqByLine.getOrDefault(id, BigDecimal.ZERO).add(new BigDecimal(String.valueOf(qty)));
                if (add.compareTo(rem) > 0)
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Line " + rowNo + ": cumulative qty " + add + " exceeds remaining " + rem);
                reqByLine.merge(id, new BigDecimal(String.valueOf(qty)), BigDecimal::add);
            }
            Long pid = ((Number) productId).longValue();
            Long wh = line.get("warehouseId") == null ? null : ((Number) line.get("warehouseId")).longValue();
            BigDecimal qb = new BigDecimal(String.valueOf(qty));
            String batchNo = line.get("batchNo") == null ? null : String.valueOf(line.get("batchNo"));
            String serialNo = (line.get("serialNo") == null || String.valueOf(line.get("serialNo")).isBlank()) ? null : String.valueOf(line.get("serialNo"));
            if (wh == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "Line " + rowNo + ": warehouse is required");
            if (batchNo == null || batchNo.isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "Line " + rowNo + ": must select an on-hand batch");
            if (isProductSerialManaged(tid, pid)) {
                if (serialNo == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "Product " + pid + " is serial-managed: select a serial number");
                if (qb.compareTo(BigDecimal.ONE) != 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Serial-managed product line qty must be 1");
                assertSerialAvailable(tid, wh, pid, batchNo, serialNo);
            } else {
                assertBatchAvailable(tid, wh, pid, batchNo, qb);
            }
        }
    }

    private void validateLoadedLines(UUID tid, Long soId, List<Tuple> lines, boolean isRed) {
        List<Map<String, Object>> mapLines = new ArrayList<>();
        for (Tuple l : lines) {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", l.get("product_id"));
            m.put("qty", l.get("qty"));
            m.put("expectedLineId", l.get("expected_line_id"));
            m.put("warehouseId", l.get("warehouse_id"));
            m.put("batchNo", l.get("batch_no"));
            m.put("serialNo", l.get("serial_no"));
            mapLines.add(m);
        }
        validateLines(tid, soId, mapLines);
        // v3.8.1 采退出库(RGI, is_red=true)：不限库存状态，仅校验在库数量足够
        if (isRed) {
            for (Tuple l : lines) {
                Long productId = ((Number) l.get("product_id")).longValue();
                Long lineWh = l.get("warehouse_id") == null ? null : ((Number) l.get("warehouse_id")).longValue();
                BigDecimal qty = new BigDecimal(String.valueOf(l.get("qty")));
                String batchNo = l.get("batch_no") == null ? null : String.valueOf(l.get("batch_no"));
                String serialNo = l.get("serial_no") == null ? null : String.valueOf(l.get("serial_no"));
                boolean isSerial = Boolean.TRUE.equals(l.get("is_serial_managed"));
                if (isSerial) assertSerialAvailableAnyStatus(tid, lineWh, productId, batchNo, serialNo);
                else assertBatchAvailableAnyStatus(tid, lineWh, productId, batchNo, qty);
            }
        }
    }

    private void assertBatchAvailableAnyStatus(UUID tid, Long warehouseId, Long productId, String batchNo, BigDecimal qty) {
        var q = em.createNativeQuery(
                "SELECT COALESCE(SUM(qty),0) AS avail FROM inventory " +
                "WHERE tenant_id = ?1 AND warehouse_id = ?2 AND product_id = ?3 AND COALESCE(batch_no,'') = COALESCE(?4,'') AND qty > 0", Tuple.class);
        q.setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId).setParameter(4, batchNo);
        Tuple t = (Tuple) q.getSingleResult();
        BigDecimal avail = new BigDecimal(String.valueOf(t.get("avail")));
        if (avail.compareTo(qty) < 0)
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "批次 " + batchNo + " 库存不足（可用 " + avail + "，需 " + qty + "）");
    }

    private void assertSerialAvailableAnyStatus(UUID tid, Long warehouseId, Long productId, String batchNo, String serialNo) {
        var q = em.createNativeQuery(
                "SELECT id FROM inventory WHERE tenant_id = ?1 AND warehouse_id = ?2 AND product_id = ?3 AND COALESCE(batch_no,'') = COALESCE(?4,'') AND serial_no = ?5 AND qty >= 1", Tuple.class);
        q.setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId).setParameter(4, batchNo).setParameter(5, serialNo);
        if (q.getResultList().isEmpty())
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "序列号 " + serialNo + " 不存在或库存不足");
    }

    private void assertBatchAvailable(UUID tid, Long warehouseId, Long productId, String batchNo, BigDecimal qty) {
        var q = em.createNativeQuery(
                "SELECT COALESCE(SUM(qty),0) AS avail FROM inventory " +
                "WHERE tenant_id = ?1 AND warehouse_id = ?2 AND product_id = ?3 AND batch_no = ?4 AND stock_status = 'QUALIFIED' AND qty > 0", Tuple.class);
        q.setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId).setParameter(4, batchNo);
        Tuple t = (Tuple) q.getSingleResult();
        BigDecimal avail = new BigDecimal(String.valueOf(t.get("avail")));
        if (avail.compareTo(qty) < 0)
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Batch " + batchNo + " qualified stock insufficient (avail " + avail + ", need " + qty + ")");
    }

    private void assertSerialAvailable(UUID tid, Long warehouseId, Long productId, String batchNo, String serialNo) {
        var q = em.createNativeQuery(
                "SELECT id FROM stock_serials WHERE tenant_id = ?1 AND warehouse_id = ?2 AND product_id = ?3 AND batch_no = ?4 AND serial_no = ?5 AND shipped_at IS NULL AND stock_status = 'QUALIFIED'", Tuple.class);
        q.setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId).setParameter(4, batchNo).setParameter(5, serialNo);
        if (q.getResultList().isEmpty())
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Serial " + serialNo + " not on hand or already shipped");
    }

    private boolean isProductSerialManaged(UUID tid, Long productId) {
        try {
            var q = em.createNativeQuery("SELECT is_serial_managed FROM products WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, productId).setParameter(2, tid);
            List<?> rs = q.getResultList();
            if (rs.isEmpty() || rs.get(0) == null) return false;
            Object v = rs.get(0);
            return v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(String.valueOf(v));
        } catch (Exception e) { return false; }
    }

    private void deductBatch(UUID tid, Long warehouseId, Long productId, String batchNo, BigDecimal qty, boolean isRed) {
        String statusCond = isRed ? "qty >= ?1" : "stock_status = 'QUALIFIED' AND qty >= ?1";
        int upd = em.createNativeQuery(
                "UPDATE inventory SET qty = qty - ?1, updated_at = now() " +
                "WHERE tenant_id = ?2 AND warehouse_id = ?3 AND product_id = ?4 AND COALESCE(batch_no,'') = COALESCE(?5,'') AND " + statusCond)
              .setParameter(1, qty).setParameter(2, tid).setParameter(3, warehouseId)
              .setParameter(4, productId).setParameter(5, batchNo).executeUpdate();
        if (upd == 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "批次 " + batchNo + " 库存不足（可能正被其它出库占用）");
    }

    private void deductSerial(UUID tid, Long warehouseId, Long productId, String batchNo, String serialNo, boolean isRed) {
        String statusCond = isRed ? "qty >= 1" : "stock_status = 'QUALIFIED' AND qty >= 1";
        int upd = em.createNativeQuery(
                "UPDATE inventory SET qty = qty - 1, updated_at = now() " +
                "WHERE tenant_id = ?1 AND warehouse_id = ?2 AND product_id = ?3 AND COALESCE(batch_no,'') = COALESCE(?4,'') AND serial_no = ?5 AND " + statusCond)
              .setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId)
              .setParameter(4, batchNo).setParameter(5, serialNo).executeUpdate();
        if (upd == 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "序列号 " + serialNo + " 库存不足或不存在");
        em.createNativeQuery(
                "UPDATE stock_serials SET shipped_at = now() " +
                "WHERE tenant_id = ?1 AND warehouse_id = ?2 AND product_id = ?3 AND COALESCE(batch_no,'') = COALESCE(?4,'') AND serial_no = ?5 AND shipped_at IS NULL")
          .setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId)
          .setParameter(4, batchNo).setParameter(5, serialNo).executeUpdate();
    }

    private String recalcSalesOutStatus(Long salesOutId) {
        var q = em.createNativeQuery(
                "SELECT COALESCE(SUM(expected_qty),0) AS exp, COALESCE(SUM(shipped_qty),0) AS shp, COALESCE(SUM(cancelled_qty),0) AS ccl " +
                "FROM sales_out_lines WHERE sales_out_id = ?1 AND COALESCE(expected_qty,0) > 0", Tuple.class);
        q.setParameter(1, salesOutId);
        Tuple t = (Tuple) q.getSingleResult();
        BigDecimal exp = new BigDecimal(String.valueOf(t.get("exp")));
        BigDecimal shp = new BigDecimal(String.valueOf(t.get("shp")));
        BigDecimal ccl = new BigDecimal(String.valueOf(t.get("ccl")));
        if (exp.signum() > 0 && shp.add(ccl).compareTo(exp) >= 0) return "COMPLETED";
        if (shp.signum() > 0) return "PARTIAL_SHIPPED";
        return "DRAFT";
    }

    private void syncOrderStatus(Long orderId, String soStatus) {
        String orderStatus;
        if ("COMPLETED".equals(soStatus)) orderStatus = "COMPLETED";
        else if ("PARTIAL_SHIPPED".equals(soStatus)) orderStatus = "SHIPPING";
        else return;
        safeUpdateOrder(orderId, orderStatus);
    }

    private void syncPurchaseReturnStatus(Long poId, String soStatus) {
        String poStatus;
        if ("COMPLETED".equals(soStatus)) poStatus = "COMPLETED";
        else if ("PARTIAL_SHIPPED".equals(soStatus)) poStatus = "SHIPPING";
        else return;
        try {
            em.createNativeQuery(
                "UPDATE purchase_orders SET status = ?1, completed_at = CASE WHEN ?1 = 'COMPLETED' THEN COALESCE(completed_at, now()) ELSE completed_at END, updated_at = now() " +
                "WHERE id = ?2 AND COALESCE(is_red,false)=true AND status IN ('APPROVED','SHIPPING','COMPLETED')")
              .setParameter(1, poStatus).setParameter(2, poId).executeUpdate();
        } catch (Exception e) {
            log.warn("Failed to sync purchase return status poId={}: {}", poId, e.getMessage());
        }
    }

    private void safeUpdateOrder(Long orderId, String orderStatus) {
        try {
            em.createNativeQuery(
                "UPDATE orders SET status = ?1, completed_at = CASE WHEN ?1 = 'COMPLETED' THEN COALESCE(completed_at, now()) ELSE completed_at END, updated_at = now() " +
                "WHERE id = ?2 AND status IN ('APPROVED','SHIPPING','COMPLETED')")
              .setParameter(1, orderStatus).setParameter(2, orderId).executeUpdate();
        } catch (Exception e) {
            log.warn("Failed to sync order status orderId={}: {}", orderId, e.getMessage());
        }
    }

    private Tuple loadOut(UUID tid, Long salesOutId) {
        var q = em.createNativeQuery(
                "SELECT id, code, status, warehouse_id, dealer_id, source_order_id, source_po_id, COALESCE(is_red,false) AS is_red FROM sales_outs WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, salesOutId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "Sales-out not found");
        return (Tuple) rs.get(0);
    }

    private Tuple loadBatch(UUID tid, Long batchId) {
        var q = em.createNativeQuery(
                "SELECT id, sales_out_id, status FROM sales_out_batches WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, batchId).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Batch not found");
        return (Tuple) rs.get(0);
    }

    private Long num(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private Integer intVal(Object o) { return o == null ? null : ((Number) o).intValue(); }
}
