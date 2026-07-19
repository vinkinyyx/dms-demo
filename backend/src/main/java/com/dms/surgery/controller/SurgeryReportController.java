/*
 * 手术植入与报台 Controller (v3.3 R9)
 *   POST /api/surgery-reports  创建手术报台单
 *   GET  /api/surgery-reports  查询手术报台单列表
 *
 * 业务规则:
 *   1. 经销商必填
 *   2. 医院必须在该经销商当前有效授权的 terminal_ids 内
 *   3. 每行产品必须指定批次(批次品)或序列号(序列号品)
 *   4. 扣减 QUALIFIED 库存
 *   5. 若登录用户是 sales，只能选自己/下级负责的经销商
 *   6. 若登录用户是 dealer，dealer_id 强制为绑定的经销商
 */
package com.dms.surgery.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.inventory.service.InventoryStatusOps;
import com.dms.org.controller.SalesOrgResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/surgery-reports")
@RequiredArgsConstructor
public class SurgeryReportController {

    private final EntityManager em;
    private final InventoryStatusOps inventoryOps;

    @GetMapping
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        UserCtx ctx = loadUserCtx(uid);
        int offset = (page - 1) * size;

        Set<Long> allowed = SalesOrgResolver.resolveAccessibleDealerIds(em, tid,
                ctx.role, ctx.salesUserId, ctx.dealerId);

        String where = "WHERE tenant_id = ?1 AND (deleted_at IS NULL)";
        if (allowed != null) {
            if (allowed.isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("total", 0L); data.put("list", Collections.emptyList());
                data.put("page", page); data.put("size", size);
                return ApiResponse.ok(data);
            }
            where += " AND dealer_id = ANY(?2)";
        }

        String cntSql = "SELECT COUNT(*) FROM surgery_reports " + where;
        var cntQ = em.createNativeQuery(cntSql);
        cntQ.setParameter(1, tid);
        if (allowed != null) cntQ.setParameter(2, allowed.toArray(new Long[0]));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        String listSql = "SELECT sr.id, sr.code, sr.dealer_id, sr.terminal_id, sr.warehouse_id, sr.sales_user_id, " +
                " sr.surgery_date, sr.patient_info, sr.doctor_name, sr.status, sr.remark, sr.created_at, sr.updated_at, sr.created_by, sr.updated_by, " +
                " d.name AS dealer_name, h.name AS terminal_name, w.name AS warehouse_name " +
                "FROM surgery_reports sr " +
                "LEFT JOIN dealers d ON d.id = sr.dealer_id " +
                "LEFT JOIN hospitals h ON h.id = sr.terminal_id " +
                "LEFT JOIN warehouses w ON w.id = sr.warehouse_id " +
                where.replace("tenant_id", "sr.tenant_id").replace("deleted_at", "sr.deleted_at").replace("dealer_id", "sr.dealer_id") +
                " ORDER BY sr.updated_at DESC NULLS LAST, sr.id DESC LIMIT ?" +
                (allowed == null ? "2" : "3") + " OFFSET ?" + (allowed == null ? "3" : "4");
        var lq = em.createNativeQuery(listSql, Tuple.class);
        lq.setParameter(1, tid);
        int idx = 2;
        if (allowed != null) { lq.setParameter(idx++, allowed.toArray(new Long[0])); }
        lq.setParameter(idx++, size);
        lq.setParameter(idx, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = lq.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("terminalId", t.get("terminal_id"));
            m.put("terminalName", t.get("terminal_name"));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("warehouseName", t.get("warehouse_name"));
            m.put("salesUserId", t.get("sales_user_id"));
            m.put("surgeryDate", com.dms.common.util.DateFmt.fmt(t.get("surgery_date")));
            m.put("patientInfo", t.get("patient_info"));
            m.put("doctorName", t.get("doctor_name"));
            m.put("status", t.get("status"));
            m.put("remark", t.get("remark"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            m.put("createdBy", t.get("created_by"));
            m.put("updatedBy", t.get("updated_by"));
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        UserCtx ctx = loadUserCtx(uid);

        Long dealerId = toLong(body.get("dealerId"));
        Long terminalId = toLong(body.get("terminalId"));
        Long warehouseId = toLong(body.get("warehouseId"));
        LocalDate surgeryDate = parseDate(body.get("surgeryDate"));
        String patientInfo = strOr(body.get("patientInfo"), "");
        String doctorName = strOr(body.get("doctorName"), "");
        String remark = strOr(body.get("remark"), "");

        // 1. 数据权限：经销商登录 -> 强制自己
        if ("dealer".equals(ctx.role)) {
            if (ctx.dealerId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "经销商账号未绑定 dealer_id");
            dealerId = ctx.dealerId;
        }
        // 2. 销售登录 -> 校验 dealer 必须在自己负责范围内
        if ("sales".equals(ctx.role)) {
            Set<Long> allowed = SalesOrgResolver.resolveAccessibleDealerIds(em, tid, ctx.role, ctx.salesUserId, ctx.dealerId);
            if (dealerId == null || allowed == null || !allowed.contains(dealerId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "无权为该经销商创建手术报台（不在您的负责范围）");
            }
        }

        if (dealerId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "经销商必填");
        if (terminalId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "终端医院必填");
        if (warehouseId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "仓库必填");
        if (surgeryDate == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "手术日期必填");

        // 3. 校验医院在经销商授权范围内
        checkTerminalAuthorized(tid, dealerId, terminalId, surgeryDate);

        // 4. 明细
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "手术明细不能为空");
        }
        // 每行必须指定 batch_no 或 serial_no (取决于产品)
        for (Map<String, Object> line : lines) {
            Long pid = toLong(line.get("productId"));
            if (pid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "产品必填");
            boolean serialManaged = isSerialManaged(tid, pid);
            String batchNo = strOr(line.get("batchNo"), null);
            String serialNo = strOr(line.get("serialNo"), null);
            if (serialManaged) {
                if (serialNo == null) throw new BusinessException(ErrorCode.PARAM_MISSING,
                        "产品 " + pid + " 是序列号管理产品，必须指定 serialNo");
            } else {
                if (batchNo == null) throw new BusinessException(ErrorCode.PARAM_MISSING,
                        "产品 " + pid + " 是批次管理产品，必须指定 batchNo");
            }
            BigDecimal qty = toBd(line.get("qty"));
            if (qty == null || qty.signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "数量必须 > 0");
            }
        }

        // 5. 主表
        String code = "SURG-" + System.currentTimeMillis();
        var ins = em.createNativeQuery(
                "INSERT INTO surgery_reports (tenant_id, code, dealer_id, terminal_id, warehouse_id, sales_user_id, " +
                "surgery_date, patient_info, doctor_name, status, remark, created_at, updated_at, created_by) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, 'COMPLETED', ?10, now(), now(), ?11) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, dealerId)
                .setParameter(4, terminalId).setParameter(5, warehouseId)
                .setParameter(6, ctx.salesUserId)
                .setParameter(7, surgeryDate).setParameter(8, patientInfo).setParameter(9, doctorName)
                .setParameter(10, remark).setParameter(11, uid);
        Long reportId = ((Number) ins.getSingleResult()).longValue();

        // 6. 明细 + 扣减 QUALIFIED 库存
        for (Map<String, Object> line : lines) {
            Long pid = toLong(line.get("productId"));
            BigDecimal qty = toBd(line.get("qty"));
            String batchNo = strOr(line.get("batchNo"), null);
            String serialNo = strOr(line.get("serialNo"), null);
            BigDecimal price = toBd(line.get("unitPrice"));

            em.createNativeQuery(
                    "INSERT INTO surgery_report_lines (report_id, product_id, qty, batch_no, serial_no, unit_price) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
                .setParameter(1, reportId).setParameter(2, pid).setParameter(3, qty)
                .setParameter(4, batchNo).setParameter(5, serialNo).setParameter(6, price)
                .executeUpdate();

            // 扣 QUALIFIED 库存 - 优先按 serial_no 精确扣
            String effBatch = serialNo != null ? serialNo : batchNo;
            inventoryOps.change(tid, pid, warehouseId, effBatch, qty.negate(),
                    "QUALIFIED", "SURGERY_USE", "surgery_report", reportId);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", reportId);
        res.put("code", code);
        res.put("dealerId", dealerId);
        res.put("terminalId", terminalId);
        res.put("surgeryDate", surgeryDate.toString());
        res.put("lineCount", lines.size());
        res.put("message", "手术报台完成，已扣减 " + lines.size() + " 项合格库存");
        return ApiResponse.ok(res);
    }

    private void checkTerminalAuthorized(UUID tid, Long dealerId, Long terminalId, LocalDate atDate) {
        var q = em.createNativeQuery(
                "SELECT terminal_ids FROM authorizations " +
                "WHERE tenant_id = ?1 AND dealer_id = ?2 AND status = 'active' " +
                "  AND valid_from <= ?3 AND valid_to >= ?3 AND deleted_at IS NULL");
        q.setParameter(1, tid).setParameter(2, dealerId).setParameter(3, atDate);
        List<?> rows = q.getResultList();
        Set<String> allowed = new HashSet<>();
        for (Object r : rows) {
            if (r == null) continue;
            for (String s : String.valueOf(r).split("[,，]")) {
                String ss = s.trim();
                if (!ss.isEmpty()) allowed.add(ss);
            }
        }
        if (allowed.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "经销商 " + dealerId + " 在 " + atDate + " 无有效授权");
        }
        if (!allowed.contains(String.valueOf(terminalId))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "医院 " + terminalId + " 不在经销商授权范围内 (授权医院: " + allowed + ")");
        }
    }

    private boolean isSerialManaged(UUID tid, Long productId) {
        try {
            var q = em.createNativeQuery(
                    "SELECT is_serial_managed FROM products WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, productId).setParameter(2, tid);
            List<?> rs = q.getResultList();
            if (rs.isEmpty() || rs.get(0) == null) return false;
            return Boolean.TRUE.equals(rs.get(0));
        } catch (Exception e) {
            return false;
        }
    }

    private UserCtx loadUserCtx(Long uid) {
        UserCtx ctx = new UserCtx();
        if (uid == null) { ctx.role = "admin"; return ctx; }
        try {
            var q = em.createNativeQuery(
                    "SELECT role, sales_user_id, dealer_id FROM users WHERE id = ?1", Tuple.class);
            q.setParameter(1, uid);
            List<?> lst = q.getResultList();
            if (!lst.isEmpty()) {
                Tuple t = (Tuple) lst.get(0);
                ctx.role = String.valueOf(t.get("role"));
                Object s = t.get("sales_user_id");
                Object d = t.get("dealer_id");
                if (s != null) ctx.salesUserId = ((Number) s).longValue();
                if (d != null) ctx.dealerId = ((Number) d).longValue();
            }
        } catch (Exception ignored) {
            ctx.role = "admin";
        }
        if (ctx.role == null || "null".equals(ctx.role)) ctx.role = "admin";
        return ctx;
    }

    private static class UserCtx {
        String role;
        Long salesUserId;
        Long dealerId;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private String strOr(Object o, String d) {
        if (o == null) return d;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? d : s;
    }
    private LocalDate parseDate(Object o) {
        if (o == null) return null;
        try { return LocalDate.parse(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
