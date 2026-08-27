package com.dms.order.service;

import com.dms.annotation.OperationLog;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.PagingUtil;
import com.dms.common.util.TenantContext;
import com.dms.order.service.support.ActionButtonSupport;
import com.dms.order.service.support.ApprovalResponseSupport;
import com.dms.execution.service.AutoDocGenerator;
import com.dms.order.dto.TransferResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {
    private final EntityManager em;
    private final AutoDocGenerator autoDocGenerator;
    private final com.dms.common.util.DocNoGenerator docNoGenerator;
    private final ApprovalService approvalService;

    /** 分页列表 */
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            int page,
            int size,
            String status,
            Long supplierId,
            Long warehouseId,
            String createdAtFrom,
            String createdAtTo,
            String updatedAtFrom,
            String updatedAtTo,
            String totalAmountFrom,
            String totalAmountTo,
            String finalAmountFrom,
            String finalAmountTo,
            String code,
            String orderType,
            String auditUserName,
            String keyword,
            String sort) {
        UUID tid = TenantContext.getTenantId();
        int safePage = PagingUtil.normalizePage(page); int safeSize = PagingUtil.normalizeSize(size); int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder(" WHERE po.tenant_id = ?1 AND po.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (status != null && !status.isBlank()) {
            where.append(" AND po.status = ?").append(idx++);
            params.add(status);
        }
        if (supplierId != null) {
            where.append(" AND po.supplier_id = ?").append(idx++);
            params.add(supplierId);
        }
        if (warehouseId != null) {
            where.append(" AND po.warehouse_id = ?").append(idx++);
            params.add(warehouseId);
        }
        if (createdAtFrom != null && !createdAtFrom.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtFrom, true); if (__t != null) { where.append(" AND po.created_at >= ?").append(idx++); params.add(__t); } }
        if (createdAtTo != null && !createdAtTo.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtTo, false); if (__t != null) { where.append(com.dms.common.util.SpecUtil.hasTime(createdAtTo) ? " AND po.created_at <= ?" : " AND po.created_at < ?").append(idx++); params.add(__t); } }
        if (updatedAtFrom != null && !updatedAtFrom.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtFrom, true); if (__t != null) { where.append(" AND po.updated_at >= ?").append(idx++); params.add(__t); } }
        if (updatedAtTo != null && !updatedAtTo.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtTo, false); if (__t != null) { where.append(com.dms.common.util.SpecUtil.hasTime(updatedAtTo) ? " AND po.updated_at <= ?" : " AND po.updated_at < ?").append(idx++); params.add(__t); } }
        if (totalAmountFrom != null && !totalAmountFrom.isBlank()) { where.append(" AND po.amount_incl_tax >= ?").append(idx++); params.add(new java.math.BigDecimal(totalAmountFrom)); }
        if (totalAmountTo != null && !totalAmountTo.isBlank()) { where.append(" AND po.amount_incl_tax <= ?").append(idx++); params.add(new java.math.BigDecimal(totalAmountTo)); }
        if (finalAmountFrom != null && !finalAmountFrom.isBlank()) { where.append(" AND po.final_amount >= ?").append(idx++); params.add(new java.math.BigDecimal(finalAmountFrom)); }
        if (finalAmountTo != null && !finalAmountTo.isBlank()) { where.append(" AND po.final_amount <= ?").append(idx++); params.add(new java.math.BigDecimal(finalAmountTo)); }
        if (code != null && !code.isBlank()) { where.append(" AND po.code ILIKE ?").append(idx++); params.add("%" + code.trim() + "%"); }
        if (orderType != null && !orderType.isBlank()) { where.append(" AND po.order_type = ?").append(idx++); params.add(orderType); }
        if (auditUserName != null && !auditUserName.isBlank()) { where.append(" AND EXISTS (SELECT 1 FROM users u2 WHERE u2.id = po.approved_by AND u2.name ILIKE ?)").append(idx++); params.add("%" + auditUserName.trim() + "%"); }
        if (keyword != null && !keyword.isBlank()) { where.append(" AND (po.code ILIKE ? OR COALESCE(NULLIF(po.supplier_name,''), s.name) ILIKE ?)").append(idx).append(idx + 1); idx += 2; String kw = "%" + keyword.trim() + "%"; params.add(kw); params.add(kw); }

        var qCnt = em.createNativeQuery("SELECT COUNT(*) FROM purchase_orders po " + where);
        for (int i = 0; i < params.size(); i++) qCnt.setParameter(i + 1, params.get(i));
        long total = ((Number) qCnt.getSingleResult()).longValue();

        String orderSql = " ORDER BY po.created_at DESC";
        if (sort != null && !sort.isBlank()) {
            String[] sp = sort.split(",");
            String f = sp[0].trim();
            String dir = sp.length > 1 && "asc".equalsIgnoreCase(sp[1].trim()) ? "ASC" : "DESC";
            switch (f) {
                case "updatedAt" -> orderSql = " ORDER BY po.updated_at " + dir;
                case "createdAt" -> orderSql = " ORDER BY po.created_at " + dir;
                case "finalAmount" -> orderSql = " ORDER BY po.final_amount " + dir;
                case "amountInclTax" -> orderSql = " ORDER BY po.amount_incl_tax " + dir;
                case "code" -> orderSql = " ORDER BY po.code " + dir;
                default -> { }
            }
        }
        String limitParam = "?" + idx++;
        String offsetParam = "?" + idx++;
        var q = em.createNativeQuery(
                "SELECT po.id, po.code, po.order_type, po.supplier_id, COALESCE(NULLIF(po.supplier_name,''), s.name) AS supplier_name, po.warehouse_id, " +
                "w.name AS warehouse_name, u.name AS audit_user_name, po.approved_at AS audit_at, " +
                "po.amount_incl_tax, po.final_amount, po.expected_date, po.status, po.extra, po.created_at, po.updated_at " +
                "FROM purchase_orders po LEFT JOIN warehouses w ON w.id = po.warehouse_id LEFT JOIN suppliers s ON s.id = po.supplier_id LEFT JOIN users u ON u.id = po.approved_by " +
                where +
                orderSql + " LIMIT " + limitParam + " OFFSET " + offsetParam,
                Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, safeSize);
        q.setParameter(params.size() + 2, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            list.add(toBrief(t));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    /** 详情（含明细） */
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> detail(Long id) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> data = readOne(id, tid);
        if (data == null) return ApiResponse.fail(40404, "采购单不存在");

        // 明细
        var q = em.createNativeQuery(
                "SELECT pol.id, pol.seq, pol.product_id, p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, " +
                "pol.qty, pol.received_qty, pol.unit_price, pol.tax_rate, pol.subtotal, pol.remark " +
                "FROM purchase_order_lines pol LEFT JOIN products p ON p.id = pol.product_id " +
                "WHERE pol.po_id = ?1 ORDER BY pol.seq", Tuple.class);
        q.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> lineRows = q.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple t : lineRows) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", t.get("id"));
            l.put("seq", t.get("seq"));
            l.put("productId", t.get("product_id"));
            l.put("productCode", t.get("p_code"));
            l.put("productName", t.get("p_name"));
            l.put("productSpec", t.get("p_spec"));
            l.put("qty", t.get("qty"));
            l.put("receivedQty", t.get("received_qty"));
            l.put("unitPrice", t.get("unit_price"));
            l.put("taxRate", t.get("tax_rate"));
            l.put("subtotal", t.get("subtotal"));
            l.put("remark", t.get("remark"));
            lines.add(l);
        }
        data.put("lines", lines);
        data.put("allowedActions", allowedActions(String.valueOf(data.get("status"))));
        return ApiResponse.ok(data);
    }

    /** 创建（DRAFT） */
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.CREATE, remark = "采购订单-创建")
    @Transactional
    public ApiResponse<Map<String, Object>> create(Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        boolean isRed = Boolean.TRUE.equals(body.get("isRed"));
        String code = docNoGenerator.next(isRed ? "RPO" : "PO");

        BigDecimal total = calcTotal(body);

        var insertPo = em.createNativeQuery(
                "INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, supplier_name, warehouse_id, is_red, " +
                "amount_incl_tax, final_amount, expected_date, status, remark, extra, created_at, updated_at) " +
                "VALUES (:tid, :code, :ot, :sid, :sname, :wid, :isred, :amt, :famt, CAST(:ed AS date), 'DRAFT', :rmk, CAST(:ext AS jsonb), now(), now()) RETURNING id");
        insertPo.setParameter("tid", tid);
        insertPo.setParameter("code", code);
        insertPo.setParameter("ot", body.getOrDefault("orderType", "NORMAL"));
        insertPo.setParameter("sid", body.get("supplierId"));
        insertPo.setParameter("sname", body.getOrDefault("supplierName", ""));
        insertPo.setParameter("wid", body.get("warehouseId"));
        insertPo.setParameter("isred", isRed);
        insertPo.setParameter("amt", total);
        insertPo.setParameter("famt", total);
        insertPo.setParameter("ed", body.get("expectedDate"));
        insertPo.setParameter("rmk", body.getOrDefault("remark", ""));
        insertPo.setParameter("ext", extraToJson(body.get("extra")));
        Long poId = ((Number) insertPo.getSingleResult()).longValue();

        insertLines(poId, body);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", poId);
        res.put("code", code);
        audit(poId, "CREATE");
        return ApiResponse.ok(res);
    }

    /**
     * 采购订单传输接口（同步）。
     *
     * <p>外部/上游系统通过本接口将采购订单一次性推送进 DMS。走 JWT 鉴权，
     * 同步事务（任何步骤失败整体回滚）。</p>
     *
     * <p>请求体（与 {@code POST /api/purchase-orders} 一致）：</p>
     *
     * <p>成功：{@code code=0}，{@code data.code} 即新采购单号（PO-20260806-00001）。</p>
     * <p>失败：{@code code!=0}，{@code message} 即失败原因。</p>
     */
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.CREATE, remark = "采购订单传输")
    @Transactional
    public ApiResponse<TransferResponse> transfer(Map<String, Object> body) {
        if (body == null) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "请求体不能为空");
        }
        if (body.get("supplierId") == null) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "supplierId 不能为空");
        }
        if (body.get("warehouseId") == null) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "warehouseId 不能为空");
        }
        Object linesObj = body.get("lines");
        if (!(linesObj instanceof List) || ((List<?>) linesObj).isEmpty()) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "采购订单明细不能为空");
        }
        for (Object lo : (List<?>) linesObj) {
            if (!(lo instanceof Map)) continue;
            Map<?, ?> lm = (Map<?, ?>) lo;
            if (lm.get("productId") == null) {
                return ApiResponse.fail(ErrorCode.PARAM_MISSING, "productId 不能为空");
            }
            if (lm.get("qty") == null) {
                return ApiResponse.fail(ErrorCode.PARAM_MISSING, "qty 不能为空");
            }
        }
        try {
            boolean isRed = Boolean.TRUE.equals(body.get("isRed"));
            String code = docNoGenerator.next(isRed ? "RPO" : "PO");
            BigDecimal total = calcTotal(body);

            var insertPo = em.createNativeQuery(
                    "INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, supplier_name, warehouse_id, is_red, " +
                    "amount_incl_tax, final_amount, expected_date, status, remark, extra, created_at, updated_at) " +
                    "VALUES (:tid, :code, :ot, :sid, :sname, :wid, :isred, :amt, :famt, CAST(:ed AS date), 'DRAFT', :rmk, CAST(:ext AS jsonb), now(), now()) RETURNING id");
            UUID tid = TenantContext.getTenantId();
            insertPo.setParameter("tid", tid);
            insertPo.setParameter("code", code);
            insertPo.setParameter("ot", body.getOrDefault("orderType", "NORMAL"));
            insertPo.setParameter("sid", body.get("supplierId"));
            insertPo.setParameter("sname", body.getOrDefault("supplierName", ""));
            insertPo.setParameter("wid", body.get("warehouseId"));
            insertPo.setParameter("isred", isRed);
            insertPo.setParameter("amt", total);
            insertPo.setParameter("famt", total);
            insertPo.setParameter("ed", body.get("expectedDate"));
            insertPo.setParameter("rmk", body.getOrDefault("remark", ""));
            insertPo.setParameter("ext", extraToJson(body.get("extra")));
            Long poId = ((Number) insertPo.getSingleResult()).longValue();

            insertLines(poId, body);
            audit(poId, "CREATE");

            log.info("[transfer] 采购订单创建成功 id={} code={} supplierId={} remark={}",
                    poId, code, body.get("supplierId"), body.get("remark"));
            TransferResponse resp = TransferResponse.builder()
                    .id(poId)
                    .code(code)
                    .orderType(String.valueOf(body.getOrDefault("orderType", "NORMAL")))
                    .status("DRAFT")
                    .amount(total)
                    .build();
            return ApiResponse.ok(resp);
        } catch (BusinessException be) {
            log.warn("[transfer] 采购订单创建失败: {}", be.getMessage());
            return ApiResponse.fail(be.getErrorCode(), be.getMessage());
        } catch (Exception e) {
            log.error("[transfer] 采购订单创建异常", e);
            return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "采购订单创建失败: " + e.getMessage());
        }
    }

    /** 更新（仅 DRAFT 可改） */
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.UPDATE, remark = "采购订单-更新")
    @Transactional
    public ApiResponse<Map<String, Object>> update(Long id, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿可编辑，当前状态: " + status);

        BigDecimal total = calcTotal(body);
        em.createNativeQuery(
                "UPDATE purchase_orders SET order_type = :ot, supplier_id = :sid, warehouse_id = :wid, " +
                "amount_incl_tax = :amt, final_amount = :famt, expected_date = CAST(:ed AS date), remark = :rmk, extra = CAST(:ext AS jsonb), updated_at = now() " +
                "WHERE id = :id AND tenant_id = :tid")
            .setParameter("ot", body.getOrDefault("orderType", "NORMAL"))
            .setParameter("sid", body.get("supplierId"))
            .setParameter("wid", body.get("warehouseId"))
            .setParameter("amt", total)
            .setParameter("famt", total)
            .setParameter("ed", body.get("expectedDate"))
            .setParameter("rmk", body.getOrDefault("remark", ""))
            .setParameter("ext", extraToJson(body.get("extra")))
            .setParameter("id", id)
            .setParameter("tid", tid)
            .executeUpdate();

        em.createNativeQuery("DELETE FROM purchase_order_lines WHERE po_id = ?1").setParameter(1, id).executeUpdate();
        insertLines(id, body);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        return ApiResponse.ok(res);
    }

    /** 提交审批 */
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.UPDATE, remark = "采购订单-提交审批")
    @Transactional
    public ApiResponse<Map<String, Object>> submit(Long id) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery("UPDATE purchase_orders SET status='PENDING_APPROVAL', submitted_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND status='DRAFT'")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "Only draft purchase order can be submitted");
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType("PURCHASE_ORDER");
            request.setBusinessId(id);
            Object code = em.createNativeQuery("SELECT code FROM purchase_orders WHERE id=?1").setParameter(1, id).getSingleResult();
            request.setBusinessCode(String.valueOf(code));
            request.setTitle("Purchase order approval: " + request.getBusinessCode());
            request.setBusinessSnapshot(buildApprovalSnapshot(id));
            ApprovalInstance instance = approvalService.start(request);
            boolean approved = "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name());
            return ApiResponse.ok(ApprovalResponseSupport.submitResult(id, instance, true));
        } catch (Exception e) {
            em.createNativeQuery("UPDATE purchase_orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2")
                    .setParameter(1, id).setParameter(2, tid).executeUpdate();
            throw e;
        }
    }

    /** 审批通过 - v3.4 增强：自动生成采购入库草稿 */
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.APPROVE, remark = "采购订单-审批通过")
    @Transactional
    public ApiResponse<Map<String, Object>> approve(Long id,
                                                     Map<String, Object> body) {
        ApprovalInstance instance = approvalService.approveBusiness("PURCHASE_ORDER", id, body == null ? null : String.valueOf(body.getOrDefault("comment", "")));
        em.createNativeQuery("UPDATE purchase_orders SET approved_by=?1 WHERE id=?2").setParameter(1, TenantContext.getUserId()).setParameter(2, id).executeUpdate();
        return ApiResponse.ok(ApprovalResponseSupport.submitResult(id, instance, false));
    }

    /** 驳回 */
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.REJECT, remark = "采购订单-驳回")
    @Transactional
    public ApiResponse<Map<String, Object>> reject(Long id,
                                                    Map<String, Object> body) {
        ApprovalInstance instance = approvalService.rejectBusiness("PURCHASE_ORDER", id, body == null ? null : String.valueOf(body.getOrDefault("comment", "")));
        return ApiResponse.ok(ApprovalResponseSupport.decisionResult(id, instance));
    }

    /** 取消（草稿或已提交可取消） */
    @Transactional
    public ApiResponse<Map<String, Object>> cancel(Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (!"DRAFT".equals(status) && !"APPROVED".equals(status)) {
            return ApiResponse.fail(40009, "当前状态不允许取消: " + status);
        }
        // v3.7.4 D1: APPROVED 状态下, 需所有关联收货单为 DRAFT 且累计已收 = 0
        if ("APPROVED".equals(status) || "RECEIVING".equals(status)) {
            Object cntObj = em.createNativeQuery(
                    "SELECT COUNT(*) FROM receipts WHERE tenant_id = ?1 AND source_po_id = ?2 AND status NOT IN ('DRAFT')")
                    .setParameter(1, tid).setParameter(2, id).getSingleResult();
            long badReceipts = ((Number) cntObj).longValue();
            if (badReceipts > 0) {
                return ApiResponse.fail(40009, "存在非草稿收货单, 不能取消采购订单");
            }
            Object rcvObj = em.createNativeQuery(
                    "SELECT COALESCE(SUM(received_qty),0) FROM purchase_order_lines WHERE po_id = ?1")
                    .setParameter(1, id).getSingleResult();
            java.math.BigDecimal rcv = new java.math.BigDecimal(String.valueOf(rcvObj));
            if (rcv.signum() > 0) {
                return ApiResponse.fail(40009, "已存在收货记录, 不能取消采购订单");
            }
        }

        em.createNativeQuery("UPDATE purchase_orders SET status='CANCELLED', updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        // v3.7.6 R3: 同步取消关联的收货入库单 (仅 DRAFT/RECEIVING 可取消，已完成保留)
        em.createNativeQuery("UPDATE receipts SET status='CANCELLED', updated_at=now() WHERE source_po_id=?1 AND tenant_id=?2 AND status IN ('DRAFT','RECEIVING','PARTIAL_RECEIVED','APPROVED')")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery("UPDATE receipt_batches SET status='CANCELLED', cancelled_at=now(), cancel_reason=COALESCE(cancel_reason,'源 PO 已取消'), updated_at=now() WHERE receipt_id IN (SELECT id FROM receipts WHERE source_po_id=?1 AND tenant_id=?2) AND status='DRAFT'")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        audit(id, "PO_CANCEL");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("status", "CANCELLED");
        return ApiResponse.ok(res);
    }

    /**
     * 采购收货入库（US-B-12）
     * 已批准的采购单 → 逐行入库 → 更新 inventory 表 + 写 inventory_transactions
     * 全部收完 → 状态 COMPLETED
     */
    @Transactional
    public ApiResponse<Map<String, Object>> receive(Long id, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (!"APPROVED".equals(status) && !"RECEIVING".equals(status)) {
            return ApiResponse.fail(40009, "仅已批准/收货中的采购单可入库，当前: " + status);
        }

        // 读取采购单信息（仓库）
        var qPo = em.createNativeQuery(
                "SELECT warehouse_id FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2");
        qPo.setParameter(1, id).setParameter(2, tid);
        List<?> pos = qPo.getResultList();
        if (pos.isEmpty()) return ApiResponse.fail(40404, "采购单不存在");
        Long warehouseId = pos.get(0) == null ? null : ((Number) pos.get(0)).longValue();

        // 收货明细 (可以指定明细行 partial，也可以整单收货)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> receiveLines = body == null ? null : (List<Map<String, Object>>) body.get("lines");

        int receivedCount = 0;
        BigDecimal totalQty = BigDecimal.ZERO;

        if (receiveLines == null || receiveLines.isEmpty()) {
            // 整单收货：把每行 (qty - received_qty) 全部收货
            var qLines = em.createNativeQuery(
                    "SELECT id, product_id, qty, received_qty FROM purchase_order_lines WHERE po_id = ?1", Tuple.class);
            qLines.setParameter(1, id);
            @SuppressWarnings("unchecked")
            List<Tuple> lines = qLines.getResultList();
            for (Tuple t : lines) {
                Long lineId = ((Number) t.get("id")).longValue();
                Long productId = ((Number) t.get("product_id")).longValue();
                BigDecimal qty = new BigDecimal(String.valueOf(t.get("qty")));
                BigDecimal received = new BigDecimal(String.valueOf(t.get("received_qty")));
                BigDecimal remaining = qty.subtract(received);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    doReceive(tid, id, lineId, productId, warehouseId, remaining, "PO-" + id);
                    receivedCount++;
                    totalQty = totalQty.add(remaining);
                }
            }
        } else {
            for (Map<String, Object> rl : receiveLines) {
                Long lineId = toLong(rl.get("lineId"));
                Long productId = toLong(rl.get("productId"));
                BigDecimal qty = new BigDecimal(String.valueOf(rl.get("qty")));
                doReceive(tid, id, lineId, productId, warehouseId, qty, "PO-" + id);
                receivedCount++;
                totalQty = totalQty.add(qty);
            }
        }

        // 检查是否全部收完 → 变 COMPLETED
        var qCheck = em.createNativeQuery(
                "SELECT COUNT(*) FROM purchase_order_lines WHERE po_id = ?1 AND received_qty < qty");
        qCheck.setParameter(1, id);
        long unfinished = ((Number) qCheck.getSingleResult()).longValue();
        String newStatus = unfinished == 0 ? "COMPLETED" : "RECEIVING";
        em.createNativeQuery("UPDATE purchase_orders SET status = ?1, updated_at = now(), completed_at = CASE WHEN ?1 = 'COMPLETED' THEN now() ELSE completed_at END WHERE id = ?2")
          .setParameter(1, newStatus).setParameter(2, id).executeUpdate();

        audit(id, "PO_RECEIVE");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("receivedLines", receivedCount);
        res.put("totalQtyReceived", totalQty);
        res.put("newStatus", newStatus);
        return ApiResponse.ok(res);
    }

    // ============ 辅助方法 ============

    private void doReceive(UUID tid, Long poId, Long lineId, Long productId, Long warehouseId, BigDecimal qty, String refCode) {
        // 1. 更新采购单明细 received_qty
        em.createNativeQuery(
                "UPDATE purchase_order_lines SET received_qty = received_qty + ?1 WHERE id = ?2")
            .setParameter(1, qty).setParameter(2, lineId).executeUpdate();

        // 2. 判断是采购入库(正向+库存) 还是 红字采购入库(采退,反向-库存)
        boolean isRed = false;
        try {
            var isRedQ = em.createNativeQuery(
                    "SELECT is_red FROM purchase_orders WHERE id = ?1");
            isRedQ.setParameter(1, poId);
            Object v = isRedQ.getSingleResult();
            isRed = v != null && Boolean.TRUE.equals(v);
        } catch (Exception ignored) {}

        BigDecimal delta = isRed ? qty.negate() : qty;
        String txnType = isRed ? "RECEIPT_RED" : "RECEIPT";
        // 采购入库 -> 库存状态为 PENDING (待检)
        // 红字采购入库 -> 相当于退货给上游，从合格库存扣减
        String targetStatus = isRed ? "QUALIFIED" : "PENDING";

        // 3. 更新 inventory (按 stock_status 分组)
        var existQ = em.createNativeQuery(
                "SELECT id, qty FROM inventory WHERE tenant_id = ?1 AND product_id = ?2 AND warehouse_id = ?3 " +
                "AND stock_status = ?4 AND (batch_no IS NULL OR batch_no = '') AND (serial_no IS NULL OR serial_no = '') LIMIT 1", Tuple.class);
        existQ.setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId).setParameter(4, targetStatus);
        @SuppressWarnings("unchecked")
        List<Tuple> exs = existQ.getResultList();
        if (exs.isEmpty()) {
            em.createNativeQuery(
                    "INSERT INTO inventory (tenant_id, product_id, warehouse_id, qty, stock_status, in_source, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, now(), now())")
                .setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId)
                .setParameter(4, delta).setParameter(5, targetStatus)
                .setParameter(6, isRed ? "PO_RED" : "PO")
                .executeUpdate();
        } else {
            Long invId = ((Number) exs.get(0).get("id")).longValue();
            em.createNativeQuery("UPDATE inventory SET qty = qty + ?1, updated_at = now() WHERE id = ?2")
                .setParameter(1, delta).setParameter(2, invId).executeUpdate();
        }

        // 4. 写事务日志
        em.createNativeQuery(
                "INSERT INTO inventory_transactions (tenant_id, product_id, warehouse_id, txn_type, qty_change, " +
                "ref_doc_type, ref_doc_id, at_time) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, 'purchase_order', ?6, now())")
            .setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId)
            .setParameter(4, txnType).setParameter(5, delta).setParameter(6, poId).executeUpdate();
    }


    private Map<String, Object> buildApprovalSnapshot(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery("SELECT code, order_type, supplier_id, warehouse_id, final_amount, amount_incl_tax, expected_date FROM purchase_orders WHERE id=?1", Tuple.class)
                .setParameter(1, id).getResultList();
        Map<String, Object> snapshot = new HashMap<>();
        if (rows.isEmpty()) return snapshot;
        Tuple row = rows.get(0);
        snapshot.put("code", row.get("code"));
        snapshot.put("orderType", row.get("order_type"));
        snapshot.put("supplierId", row.get("supplier_id"));
        snapshot.put("warehouseId", row.get("warehouse_id"));
        snapshot.put("finalAmount", row.get("final_amount"));
        snapshot.put("amountInclTax", row.get("amount_incl_tax"));
        snapshot.put("expectedDate", row.get("expected_date"));
        return snapshot;
    }
    private ApiResponse<Map<String, Object>> doTransition(Long id, String fromStatus, String toStatus, String action) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery(
                "UPDATE purchase_orders SET status = ?1, updated_at = now(), " +
                "submitted_at = CASE WHEN ?1 = 'SUBMITTED' THEN now() ELSE submitted_at END " +
                "WHERE id = ?2 AND tenant_id = ?3 AND status = ?4")
            .setParameter(1, toStatus).setParameter(2, id).setParameter(3, tid).setParameter(4, fromStatus)
            .executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "状态不允许该操作，需要当前状态为 " + fromStatus);
        audit(id, action);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("newStatus", toStatus);
        return ApiResponse.ok(res);
    }

    private void audit(Long id, String action) {
        try {
            em.createNativeQuery(
                    "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, ip, at_time) " +
                    "VALUES (?1, ?2, ?3, 'purchase_order', ?4, '127.0.0.1', now())")
                .setParameter(1, TenantContext.getTenantId()).setParameter(2, TenantContext.getUserId())
                .setParameter(3, action).setParameter(4, String.valueOf(id))
                .executeUpdate();
        } catch (Exception ignored) {}
    }

    private String getStatus(Long id, UUID tid) {
        try {
            var q = em.createNativeQuery(
                    "SELECT status FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, id).setParameter(2, tid);
            return String.valueOf(q.getSingleResult());
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> readOne(Long id, UUID tid) {
        try {
            var q = em.createNativeQuery(
                    "SELECT id, code, order_type, supplier_id, COALESCE(NULLIF(supplier_name,''), (SELECT name FROM suppliers WHERE id=supplier_id)) AS supplier_name, warehouse_id, " +
                    "amount_incl_tax, final_amount, expected_date, status, remark, extra, " +
                    "created_at, updated_at, submitted_at, approved_at, completed_at " +
                    "FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
            q.setParameter(1, id).setParameter(2, tid);
            @SuppressWarnings("unchecked")
            List<Tuple> rs = q.getResultList();
            if (rs.isEmpty()) return null;
            return toBrief(rs.get(0));
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> toBrief(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("code", t.get("code"));
        m.put("orderType", t.get("order_type"));
        m.put("supplierId", t.get("supplier_id"));
        try { m.put("supplierName", t.get("supplier_name")); } catch (Exception ignored) {}
        try { m.put("warehouseId", t.get("warehouse_id")); } catch (Exception ignored) {}
        try { m.put("warehouseName", t.get("warehouse_name")); } catch (Exception ignored) {}
        try { m.put("auditUserName", t.get("audit_user_name")); } catch (Exception ignored) {}
        try { m.put("auditAt", com.dms.common.util.DateFmt.fmt(t.get("audit_at"))); } catch (Exception ignored) {}
        m.put("amountInclTax", t.get("amount_incl_tax"));
        m.put("finalAmount", t.get("final_amount"));
        try { m.put("expectedDate", com.dms.common.util.DateFmt.fmt(t.get("expected_date"))); } catch (Exception ignored) {}
        m.put("status", t.get("status"));
        try { m.put("remark", t.get("remark")); } catch (Exception ignored) {}
        try { m.put("extra", t.get("extra")); } catch (Exception ignored) {}
        try { m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at"))); } catch (Exception ignored) {}
        try { m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at"))); } catch (Exception ignored) {}
        try { m.put("submittedAt", com.dms.common.util.DateFmt.fmt(t.get("submitted_at"))); } catch (Exception ignored) {}
        try { m.put("approvedAt", com.dms.common.util.DateFmt.fmt(t.get("approved_at"))); } catch (Exception ignored) {}
        try { m.put("completedAt", com.dms.common.util.DateFmt.fmt(t.get("completed_at"))); } catch (Exception ignored) {}
        return m;
    }

    private BigDecimal calcTotal(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Map<String, Object> l : lines) {
                BigDecimal qty = new BigDecimal(String.valueOf(l.getOrDefault("qty", "0")));
                BigDecimal price = new BigDecimal(String.valueOf(l.getOrDefault("unitPrice", "0")));
                total = total.add(qty.multiply(price));
            }
        }
        return total;
    }

    private void insertLines(Long poId, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null) return;
        int seq = 1;
        for (Map<String, Object> l : lines) {
            if (l.get("productId") == null) continue;
            BigDecimal qty = new BigDecimal(String.valueOf(l.getOrDefault("qty", "0")));
            BigDecimal price = new BigDecimal(String.valueOf(l.getOrDefault("unitPrice", "0")));
            BigDecimal tax = new BigDecimal(String.valueOf(l.getOrDefault("taxRate", "0.13")));
            BigDecimal sub = qty.multiply(price);
            em.createNativeQuery(
                    "INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, unit_price, tax_rate, subtotal, remark, created_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, now())")
                .setParameter(1, poId).setParameter(2, seq++)
                .setParameter(3, toLong(l.get("productId")))
                .setParameter(4, qty).setParameter(5, price).setParameter(6, tax).setParameter(7, sub)
                .setParameter(8, l.getOrDefault("remark", ""))
                .executeUpdate();
        }
    }

    /** 状态机 → 允许的操作 */
    private List<Map<String, Object>> allowedActions(String status) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if ("DRAFT".equals(status)) {
            actions.add(ActionButtonSupport.action("edit", "编辑", "primary", "PUT", ""));
            actions.add(ActionButtonSupport.action("submit", "提交审批", "warn", "POST", "/submit"));
            actions.add(ActionButtonSupport.action("cancel", "取消", "danger", "POST", "/cancel"));
        } else if ("SUBMITTED".equals(status)) {
            actions.add(ActionButtonSupport.action("approve", "审批通过", "success", "POST", "/approve"));
            actions.add(ActionButtonSupport.action("reject", "驳回", "danger", "POST", "/reject"));
        } else if ("APPROVED".equals(status)) {
            actions.add(ActionButtonSupport.action("cancel", "取消", "warning", "POST", "/cancel"));
            actions.add(ActionButtonSupport.action("receive", "收货入库", "success", "POST", "/receive"));
        } else if ("RECEIVING".equals(status)) {
            actions.add(ActionButtonSupport.action("receive", "继续收货", "success", "POST", "/receive"));
        }
        // COMPLETED / REJECTED / CANCELLED - 无可执行操作，只能查看
        return actions;
    }

    
    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o)); }
        catch (NumberFormatException e) { throw new BusinessException(ErrorCode.PARAM_INVALID, "ID 格式非法: " + o); }
    }

    private String strOr(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }

    private BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(String.valueOf(o)); }
        catch (NumberFormatException e) { throw new BusinessException(ErrorCode.PARAM_INVALID, "采购/采退单ID格式非法: " + o); }
    }
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.DELETE, remark = "采购订单-删除")
    @Transactional
    public ApiResponse<Void> delete(Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "采购订单不存在");
        if (!"DRAFT".equals(status)) {
            return ApiResponse.fail(40009, "仅草稿状态可删除，当前状态: " + status);
        }
        int aff = em.createNativeQuery("UPDATE purchase_orders SET deleted_at = now() WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (aff == 0) return ApiResponse.fail(40404, "采购单不存在");
        return ApiResponse.ok();
    }
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        String whereQualified = " WHERE po.tenant_id = ?1 AND po.deleted_at IS NULL";
        var q = em.createNativeQuery(
                "SELECT po.id, po.code, po.order_type, po.supplier_id, COALESCE(NULLIF(po.supplier_name,''), s.name) AS supplier_name, po.warehouse_id, " +
                "w.name AS warehouse_name, " +
                "po.amount_incl_tax, po.final_amount, po.expected_date, po.status, po.extra, po.created_at " +
                "FROM purchase_orders po LEFT JOIN warehouses w ON w.id = po.warehouse_id LEFT JOIN suppliers s ON s.id = po.supplier_id " +
                whereQualified +
                " ORDER BY po.created_at DESC",
                Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            list.add(toBrief(t));
        }

        String[] headers = {"ID", "采购单号", "订单类型", "供应商ID", "供应商名称", "仓库ID", "仓库名称", "含税金额", "最终金额", "期望到货", "状态", "创建时间"};
        String[] fieldNames = {"id", "code", "orderType", "supplierId", "supplierName", "warehouseId", "warehouseName", "amountInclTax", "finalAmount", "expectedDate", "status", "createdAt"};

        byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("采购订单列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }
    @Transactional
    public ApiResponse<java.util.Map<String, Object>> batchImport(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "请选择要导入的文件");
        }

        java.util.List<java.util.Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "Excel 文件中没有数据");
        }

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                String orderType = strOr(row.get("订单类型"), "NORMAL");
                Long supplierId = toLong(row.get("供应商ID"));
                Long warehouseId = toLong(row.get("仓库ID"));
                java.math.BigDecimal amountInclTax = toBd(row.get("含税金额"));
                java.math.BigDecimal finalAmount = toBd(row.get("最终金额"));
                String expectedDate = strOr(row.get("期望到货"), null);
                String status = strOr(row.get("状态"), "DRAFT");

                if (supplierId == null) {
                    throw new IllegalArgumentException("供应商ID不能为空");
                }
                if (warehouseId == null) {
                    throw new IllegalArgumentException("仓库ID不能为空");
                }

                String sql = "INSERT INTO purchase_orders (order_type, supplier_id, warehouse_id, amount_incl_tax, final_amount, expected_date, status, tenant_id) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)";
                em.createNativeQuery(sql)
                        .setParameter(1, orderType != null ? orderType : "NORMAL")
                        .setParameter(2, supplierId)
                        .setParameter(3, warehouseId)
                        .setParameter(4, amountInclTax)
                        .setParameter(5, finalAmount)
                        .setParameter(6, expectedDate)
                        .setParameter(7, status != null ? status : "DRAFT")
                        .setParameter(8, TenantContext.getTenantId())
                        .executeUpdate();
                success++;
            } catch (Exception e) {
                failed++;
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("row", i + 2);
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", data.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return ApiResponse.ok(result);
    }

    private String extraToJson(Object extra) {
        if (extra == null) return "{}";
        if (extra instanceof String) return (String) extra;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra);
        } catch (Exception e) { return "{}"; }
    }

}
