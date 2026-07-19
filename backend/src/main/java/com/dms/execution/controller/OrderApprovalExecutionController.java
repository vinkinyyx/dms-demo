/*
 * 订单审批 & 自动生成出/入库单 Controller (v3.4 R5)
 *
 *   POST /api/orders/{id}/approve         → APPROVED + 自动建销售出库(草稿)
 *   POST /api/purchase-orders/{id}/approve → APPROVED + 自动建采购入库(草稿)
 *
 *   POST /api/sales-outs/{id}/execute      → 执行(扣QUALIFIED库存)
 *   POST /api/sales-outs/{id}/cancel       → 取消草稿
 *   POST /api/receipts/{id}/execute        → 执行(加PENDING库存)
 *   POST /api/receipts/{id}/cancel         → 取消草稿
 */
package com.dms.execution.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.OperationLogService;
import com.dms.inventory.service.InventoryStatusOps;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OrderApprovalExecutionController {

    private final EntityManager em;
    private final InventoryStatusOps inventoryOps;
    private final OperationLogService opLog;

    /**
     * 审批订单（销售/销退）→ 状态=APPROVED → 自动生成销售出库草稿单
     */
    @PostMapping("/api/orders-approval/{id}/approve")
    @Transactional
    public ApiResponse<Map<String, Object>> approveOrder(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, status, is_red, dealer_id, ref_order_id FROM orders WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        Tuple t = (Tuple) rs.get(0);
        String status = String.valueOf(t.get("status"));
        if ("APPROVED".equals(status) || "COMPLETED".equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "订单已审批，不能重复");
        }
        boolean isRed = Boolean.TRUE.equals(t.get("is_red"));
        Long dealerId = t.get("dealer_id") == null ? null : ((Number) t.get("dealer_id")).longValue();

        // 更新订单
        em.createNativeQuery("UPDATE orders SET status = 'APPROVED', approved_at = now(), updated_at = now() WHERE id = ?1")
                .setParameter(1, id).executeUpdate();

        // 自动建单：销售出库草稿
        String code = (isRed ? "SR-" : "SO-") + System.currentTimeMillis();
        var ins = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id, code, dealer_id, is_red, status, auto_created, source_order_id, sales_date, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, 'DRAFT', true, ?5, now(), now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, dealerId)
                .setParameter(4, isRed).setParameter(5, id);
        Long soId = ((Number) ins.getSingleResult()).longValue();

        // 拷贝明细
        var lineQ = em.createNativeQuery(
                "SELECT product_id, qty FROM order_lines WHERE order_id = ?1", Tuple.class);
        lineQ.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lineQ.getResultList();
        int copied = 0;
        for (Tuple l : ls) {
            em.createNativeQuery(
                    "INSERT INTO sales_out_lines (sales_out_id, product_id, qty) VALUES (?1, ?2, ?3)")
                .setParameter(1, soId).setParameter(2, l.get("product_id")).setParameter(3, l.get("qty"))
                .executeUpdate();
            copied++;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", id);
        res.put("status", "APPROVED");
        res.put("autoCreatedSalesOutId", soId);
        res.put("autoCreatedCode", code);
        res.put("lineCount", copied);
        res.put("message", "订单已审批，已自动生成" + (isRed ? "红字" : "") + "销售出库草稿单 " + code);
        return ApiResponse.ok(res);
    }

    /**
     * 审批采购单（采购/采退）→ 自动生成采购入库草稿
     */
    @PostMapping("/api/purchase-orders-approval/{id}/approve")
    @Transactional
    public ApiResponse<Map<String, Object>> approvePO(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, status, is_red, supplier_id, warehouse_id FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2",
                Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "采购单不存在");
        Tuple t = (Tuple) rs.get(0);
        String status = String.valueOf(t.get("status"));
        if ("APPROVED".equals(status) || "COMPLETED".equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "采购单已审批");
        }
        boolean isRed = Boolean.TRUE.equals(t.get("is_red"));
        Long supplierId = t.get("supplier_id") == null ? null : ((Number) t.get("supplier_id")).longValue();
        Long whId = t.get("warehouse_id") == null ? null : ((Number) t.get("warehouse_id")).longValue();

        em.createNativeQuery("UPDATE purchase_orders SET status = 'APPROVED', approved_at = now(), updated_at = now() WHERE id = ?1")
                .setParameter(1, id).executeUpdate();

        // 自动建单：receipts 草稿
        String code = (isRed ? "PR-" : "RC-") + System.currentTimeMillis();
        var ins = em.createNativeQuery(
                "INSERT INTO receipts (tenant_id, code, ref_doc_type, ref_doc_id, is_red, status, auto_created, source_po_id, warehouse_id, created_at, updated_at) " +
                "VALUES (?1, ?2, 'purchase_order', ?3, ?4, 'DRAFT', true, ?3, ?5, now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, id)
                .setParameter(4, isRed).setParameter(5, whId);
        Long rcId = ((Number) ins.getSingleResult()).longValue();

        // 拷贝明细到 receipt_lines (expected_qty)
        var lineQ = em.createNativeQuery(
                "SELECT id, product_id, qty FROM purchase_order_lines WHERE po_id = ?1", Tuple.class);
        lineQ.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lineQ.getResultList();
        int copied = 0;
        for (Tuple l : ls) {
            try {
                em.createNativeQuery(
                        "INSERT INTO receipt_lines (receipt_id, product_id, expected_qty, received_qty) " +
                        "VALUES (?1, ?2, ?3, 0)")
                    .setParameter(1, rcId).setParameter(2, l.get("product_id"))
                    .setParameter(3, l.get("qty"))
                    .executeUpdate();
                copied++;
            } catch (Exception ex) { log.warn("拷贝明细失败: {}", ex.getMessage()); }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("purchaseOrderId", id);
        res.put("status", "APPROVED");
        res.put("autoCreatedReceiptId", rcId);
        res.put("autoCreatedCode", code);
        res.put("lineCount", copied);
        res.put("message", "采购单已审批，已自动生成" + (isRed ? "红字" : "") + "采购入库草稿单 " + code);
        return ApiResponse.ok(res);
    }

    /**
     * 执行销售出库（草稿→完成 + 扣库存）
     * body: { lines: [{ salesOutLineId, batchNo/serialNo }...] }  (指定要出的批次/序列号)
     */
    @PostMapping("/api/sales-outs/{id}/execute")
    @Transactional
    public ApiResponse<Map<String, Object>> executeSalesOut(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, status, is_red, warehouse_id, dealer_id FROM sales_outs WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");
        Tuple t = (Tuple) rs.get(0);
        String status = String.valueOf(t.get("status"));
        // v3.4.10: 允许 DRAFT / PARTIAL_SHIPPED
        if (!"DRAFT".equals(status) && !"PARTIAL_SHIPPED".equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "当前状态不能发货：" + status);
        }
        boolean isRed = Boolean.TRUE.equals(t.get("is_red"));
        Long whId = t.get("warehouse_id") == null ? null : ((Number) t.get("warehouse_id")).longValue();

        var lineQ = em.createNativeQuery(
                "SELECT sl.id, sl.product_id, sl.qty, COALESCE(sl.shipped_qty,0) AS shipped_qty, sl.batch_no, sl.serial_no, sl.warehouse_id, " +
                "COALESCE(p.is_serial_managed,false) AS is_serial " +
                "FROM sales_out_lines sl LEFT JOIN products p ON p.id = sl.product_id WHERE sl.sales_out_id = ?1",
                Tuple.class);
        lineQ.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lineQ.getResultList();

        // v3.4.11: 前端 lines 支持同一 salesOutLineId 多条子录入（序列号逐件出库）
        Map<Long, List<Map<String, Object>>> ovList = new HashMap<>();
        if (body != null && body.get("lines") instanceof List) {
            for (Object o : (List<?>) body.get("lines")) {
                if (o instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    Long lid = toLong(m.get("salesOutLineId"));
                    if (lid == null) continue;
                    Map<String, Object> vv = new HashMap<>();
                    Object b = m.get("batchNo"); if (b != null) vv.put("batch", String.valueOf(b));
                    Object s = m.get("serialNo"); if (s != null) vv.put("serial", String.valueOf(s));
                    Object qq = m.get("qty"); if (qq != null) vv.put("qty", qq);
                    ovList.computeIfAbsent(lid, k -> new ArrayList<>()).add(vv);
                }
            }
        }

        int changed = 0;
        boolean allDone = true;
        java.util.Set<String> seenSerials = new java.util.HashSet<>();
        Long uid = TenantContext.getUserId();
        // v3.4.12: 本次执行批次序号（该单已有执行条数 + 1 起）
        Number existExec = (Number) em.createNativeQuery(
                "SELECT COALESCE(MAX(seq_no),0) FROM sales_out_execution_lines WHERE sales_out_id = ?1")
                .setParameter(1, id).getSingleResult();
        int seqBase = existExec.intValue();
        for (Tuple l : ls) {
            Long lineId = ((Number) l.get("id")).longValue();
            Long pid = ((Number) l.get("product_id")).longValue();
            BigDecimal expected = (BigDecimal) l.get("qty");
            BigDecimal shipped = (BigDecimal) l.get("shipped_qty");
            if (shipped == null) shipped = BigDecimal.ZERO;
            boolean isSerial = Boolean.TRUE.equals(l.get("is_serial"));
            Long lineWh = l.get("warehouse_id") == null ? whId : ((Number) l.get("warehouse_id")).longValue();
            if (lineWh == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "明细行 " + lineId + " 无 warehouse_id");
            BigDecimal remaining = expected.subtract(shipped);

            List<Map<String, Object>> entries = ovList.get(lineId);
            if (entries == null || entries.isEmpty()) {
                if (remaining.compareTo(BigDecimal.ZERO) > 0) allDone = false;
                continue;
            }

            BigDecimal lineThisTotal = BigDecimal.ZERO;
            for (Map<String, Object> e : entries) {
                String batchNo = e.containsKey("batch") ? (String) e.get("batch") : (String) l.get("batch_no");
                String serialNo = e.containsKey("serial") ? (String) e.get("serial") : (String) l.get("serial_no");
                BigDecimal thisQty = null;
                if (e.containsKey("qty")) {
                    try { thisQty = new BigDecimal(String.valueOf(e.get("qty"))); } catch (Exception ignored){}
                }
                if (thisQty == null) thisQty = remaining.subtract(lineThisTotal);
                if (thisQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                if (isSerial) {
                    if (serialNo == null || serialNo.isEmpty())
                        throw new BusinessException(ErrorCode.PARAM_MISSING, "产品 " + pid + " 为序列号管理，必须逐件选择序列号");
                    if (thisQty.compareTo(BigDecimal.ONE) != 0)
                        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "序列号 " + serialNo + " 数量必须为 1");
                    if (!seenSerials.add(serialNo))
                        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "序列号重复: " + serialNo);
                } else {
                    if ((batchNo == null || batchNo.isEmpty()))
                        throw new BusinessException(ErrorCode.PARAM_MISSING, "产品 " + pid + " 必须指定批次号");
                }

                lineThisTotal = lineThisTotal.add(thisQty);
                if (lineThisTotal.compareTo(remaining) > 0)
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "产品 " + pid + " 本次发货合计 " + lineThisTotal + " 超过剩余 " + remaining);

                String effKey = (serialNo != null && !serialNo.isEmpty()) ? serialNo : batchNo;
                if (isRed) {
                    inventoryOps.change(tid, pid, lineWh, effKey, thisQty.abs(),
                            "PENDING", "SALES_OUT_RED", "sales_out", id);
                } else {
                    inventoryOps.change(tid, pid, lineWh, effKey, thisQty.abs().negate(),
                            "QUALIFIED", "SALES_OUT", "sales_out", id);
                }
                // v3.4.12: 每笔发货留一条执行明细
                em.createNativeQuery(
                        "INSERT INTO sales_out_execution_lines (tenant_id, sales_out_id, sales_out_line_id, product_id, batch_no, serial_no, qty, seq_no, operator_id, created_at) " +
                        "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9, now())")
                        .setParameter(1, tid).setParameter(2, id).setParameter(3, lineId).setParameter(4, pid)
                        .setParameter(5, batchNo).setParameter(6, serialNo).setParameter(7, thisQty)
                        .setParameter(8, ++seqBase).setParameter(9, uid).executeUpdate();
                changed++;
            }

            if (lineThisTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newShipped = shipped.add(lineThisTotal);
                Map<String, Object> last = entries.get(entries.size() - 1);
                String lastBatch = last.containsKey("batch") ? (String) last.get("batch") : (String) l.get("batch_no");
                String lastSerial = last.containsKey("serial") ? (String) last.get("serial") : (String) l.get("serial_no");
                em.createNativeQuery("UPDATE sales_out_lines SET batch_no = ?1, serial_no = ?2, shipped_qty = ?3 WHERE id = ?4")
                        .setParameter(1, lastBatch).setParameter(2, lastSerial).setParameter(3, newShipped).setParameter(4, lineId).executeUpdate();
                if (newShipped.compareTo(expected) < 0) allDone = false;
            } else {
                if (remaining.compareTo(BigDecimal.ZERO) > 0) allDone = false;
            }
        }

        String newStatus = allDone ? "COMPLETED" : "PARTIAL_SHIPPED";
        em.createNativeQuery("UPDATE sales_outs SET status = ?1, updated_at = now() WHERE id = ?2")
                .setParameter(1, newStatus).setParameter(2, id).executeUpdate();
        opLog.log("sales_out", id, allDone ? "SALES_OUT" : "SALES_OUT_PARTIAL",
                (isRed ? "红字" : "") + "发货执行，本次" + changed + "条，单据状态=" + newStatus);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("status", newStatus); res.put("lineCount", changed);
        res.put("message", (isRed ? "红字" : "") + "销售出库已执行" + (allDone ? "（全部完成）" : "（部分发货，剩余可继续）"));
        return ApiResponse.ok(res);
    }

    @PostMapping("/api/sales-outs/{id}/cancel-draft")
    @Transactional
    public ApiResponse<Map<String, Object>> cancelSalesOut(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        // v3.4.10: DRAFT 或 PARTIAL_SHIPPED 均可取消（已发货部分保留）
        int updated = em.createNativeQuery(
                "UPDATE sales_outs SET status = 'CANCELLED', updated_at = now() " +
                "WHERE id = ?1 AND tenant_id = ?2 AND status IN ('DRAFT','PARTIAL_SHIPPED')")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (updated == 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "只有草稿或部分发货状态可取消");
        opLog.log("sales_out", id, "CANCEL", "销售出库取消，单据状态=CANCELLED");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("status", "CANCELLED");
        return ApiResponse.ok(res);
    }

    /**
     * v3.4.10 执行采购入库（支持分次收货）
     * body.lines[i].qty 为本次收货量（可小于 expected_qty）
     */
    @PostMapping("/api/receipts/{id}/execute")
    @Transactional
    public ApiResponse<Map<String, Object>> executeReceipt(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, status, is_red, warehouse_id FROM receipts WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        Tuple t = (Tuple) rs.get(0);
        String status = String.valueOf(t.get("status"));
        // v3.4.10: 允许 DRAFT / PARTIAL_RECEIVED 状态
        if (!"DRAFT".equals(status) && !"PARTIAL_RECEIVED".equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "当前状态不能执行收货: " + status);
        }
        boolean isRed = Boolean.TRUE.equals(t.get("is_red"));
        Long whId = t.get("warehouse_id") == null ? null : ((Number) t.get("warehouse_id")).longValue();

        var lineQ = em.createNativeQuery(
                "SELECT rl.id, rl.product_id, rl.expected_qty, COALESCE(rl.received_qty,0) AS received_qty, rl.batch_no, rl.serial_no, " +
                "COALESCE(p.is_serial_managed,false) AS is_serial " +
                "FROM receipt_lines rl LEFT JOIN products p ON p.id = rl.product_id WHERE rl.receipt_id = ?1",
                Tuple.class);
        lineQ.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lineQ.getResultList();

        // v3.4.11: 前端 lines 支持同一 receiptLineId 多条子录入（序列号产品逐件录入）
        // [{ receiptLineId, batchNo, serialNo, qty }]  同一 lineId 可出现多次
        Map<Long, List<Map<String, Object>>> ovList = new HashMap<>();
        if (body != null && body.get("lines") instanceof List) {
            for (Object o : (List<?>) body.get("lines")) {
                if (o instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    Long lid = toLong(m.get("receiptLineId"));
                    if (lid == null) continue;
                    Map<String, Object> vv = new HashMap<>();
                    Object b = m.get("batchNo"); if (b != null) vv.put("batch", String.valueOf(b));
                    Object s = m.get("serialNo"); if (s != null) vv.put("serial", String.valueOf(s));
                    Object qq = m.get("qty"); if (qq != null) vv.put("qty", qq);
                    ovList.computeIfAbsent(lid, k -> new ArrayList<>()).add(vv);
                }
            }
        }

        int changed = 0;
        boolean allDone = true;
        java.util.Set<String> seenSerials = new java.util.HashSet<>();
        Long uid = TenantContext.getUserId();
        Number existExec = (Number) em.createNativeQuery(
                "SELECT COALESCE(MAX(seq_no),0) FROM receipt_execution_lines WHERE receipt_id = ?1")
                .setParameter(1, id).getSingleResult();
        int seqBase = existExec.intValue();
        for (Tuple l : ls) {
            Long lineId = ((Number) l.get("id")).longValue();
            Long pid = ((Number) l.get("product_id")).longValue();
            BigDecimal expected = (BigDecimal) l.get("expected_qty");
            BigDecimal alreadyReceived = (BigDecimal) l.get("received_qty");
            if (alreadyReceived == null) alreadyReceived = BigDecimal.ZERO;
            boolean isSerial = Boolean.TRUE.equals(l.get("is_serial"));
            Long lineWh = whId;
            if (lineWh == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "明细行 " + lineId + " 无 warehouse_id");
            BigDecimal remaining = expected.subtract(alreadyReceived);

            List<Map<String, Object>> entries = ovList.get(lineId);
            // 该行本次没有任何录入 → 跳过（保留剩余，单据不完成）
            if (entries == null || entries.isEmpty()) {
                if (remaining.compareTo(BigDecimal.ZERO) > 0) allDone = false;
                continue;
            }

            BigDecimal lineThisTotal = BigDecimal.ZERO;
            for (Map<String, Object> e : entries) {
                String batchNo = e.containsKey("batch") ? (String) e.get("batch") : (String) l.get("batch_no");
                String serialNo = e.containsKey("serial") ? (String) e.get("serial") : (String) l.get("serial_no");
                BigDecimal thisQty = null;
                if (e.containsKey("qty")) {
                    try { thisQty = new BigDecimal(String.valueOf(e.get("qty"))); } catch (Exception ignored){}
                }
                if (thisQty == null) thisQty = remaining.subtract(lineThisTotal);
                if (thisQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 序列号产品：一个序列号=一件，本条 qty 必须为 1
                if (isSerial) {
                    if (serialNo == null || serialNo.isEmpty())
                        throw new BusinessException(ErrorCode.PARAM_MISSING, "产品 " + pid + " 为序列号管理，必须逐件录入序列号");
                    if (thisQty.compareTo(BigDecimal.ONE) != 0)
                        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "序列号 " + serialNo + " 数量必须为 1");
                    if (!seenSerials.add(serialNo))
                        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "序列号重复: " + serialNo);
                } else {
                    if ((batchNo == null || batchNo.isEmpty()))
                        throw new BusinessException(ErrorCode.PARAM_MISSING, "产品 " + pid + " 必须录入批次号");
                }

                lineThisTotal = lineThisTotal.add(thisQty);
                if (lineThisTotal.compareTo(remaining) > 0)
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "产品 " + pid + " 本次收货合计 " + lineThisTotal + " 超过剩余 " + remaining);

                String effKey = (serialNo != null && !serialNo.isEmpty()) ? serialNo : batchNo;
                if (isRed) {
                    inventoryOps.change(tid, pid, lineWh, effKey, thisQty.abs().negate(),
                            "QUALIFIED", "RECEIPT_RED", "receipt", id);
                } else {
                    inventoryOps.change(tid, pid, lineWh, effKey, thisQty.abs(),
                            "PENDING", "RECEIPT", "receipt", id);
                }
                // v3.4.12: 每笔收货留一条执行明细
                em.createNativeQuery(
                        "INSERT INTO receipt_execution_lines (tenant_id, receipt_id, receipt_line_id, product_id, batch_no, serial_no, qty, seq_no, operator_id, created_at) " +
                        "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9, now())")
                        .setParameter(1, tid).setParameter(2, id).setParameter(3, lineId).setParameter(4, pid)
                        .setParameter(5, batchNo).setParameter(6, serialNo).setParameter(7, thisQty)
                        .setParameter(8, ++seqBase).setParameter(9, uid).executeUpdate();
                changed++;
            }

            if (lineThisTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newReceived = alreadyReceived.add(lineThisTotal);
                // 记录最后一条批次/序列号到主明细（追溯明细以库存流水为准）
                Map<String, Object> last = entries.get(entries.size() - 1);
                String lastBatch = last.containsKey("batch") ? (String) last.get("batch") : (String) l.get("batch_no");
                String lastSerial = last.containsKey("serial") ? (String) last.get("serial") : (String) l.get("serial_no");
                em.createNativeQuery("UPDATE receipt_lines SET batch_no = ?1, serial_no = ?2, received_qty = ?3 WHERE id = ?4")
                        .setParameter(1, lastBatch).setParameter(2, lastSerial).setParameter(3, newReceived).setParameter(4, lineId).executeUpdate();
                if (newReceived.compareTo(expected) < 0) allDone = false;
            } else {
                if (remaining.compareTo(BigDecimal.ZERO) > 0) allDone = false;
            }
        }

        String newStatus = allDone ? "COMPLETED" : "PARTIAL_RECEIVED";
        em.createNativeQuery("UPDATE receipts SET status = ?1, updated_at = now() WHERE id = ?2")
                .setParameter(1, newStatus).setParameter(2, id).executeUpdate();
        opLog.log("receipt", id, allDone ? "RECEIPT" : "RECEIPT_PARTIAL",
                (isRed ? "红字" : "") + "收货执行，本次" + changed + "条，单据状态=" + newStatus);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("status", newStatus); res.put("lineCount", changed);
        res.put("message", (isRed ? "红字" : "") + "采购入库已执行" + (allDone ? "（全部完成）" : "（部分收货，剩余可继续）"));
        return ApiResponse.ok(res);
    }

    @PostMapping("/api/receipts/{id}/cancel-draft")
    @Transactional
    public ApiResponse<Map<String, Object>> cancelReceipt(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int updated = em.createNativeQuery(
                "UPDATE receipts SET status = 'CANCELLED', updated_at = now() " +
                "WHERE id = ?1 AND tenant_id = ?2 AND status IN ('DRAFT','PARTIAL_RECEIVED')")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (updated == 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "只有草稿或部分收货状态可取消");
        opLog.log("receipt", id, "CANCEL", "收货入库取消剩余，单据状态=CANCELLED");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("status", "CANCELLED");
        return ApiResponse.ok(res);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
