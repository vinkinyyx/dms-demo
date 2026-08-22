/*
 * 手术植入与报台 Controller (v3.3 R9)
 *   POST /api/surgery-reports  创建手术报台单
 *   GET  /api/surgery-reports  查询手术报台单列表
 *
 * 业务规则:
 *   1. 经销商必填
 *   2. 医院可选任意未删除的 hospital（v3.8.7 去授权校验）
 *   3. 每行产品必须指定批次(批次品)或序列号(序列号品)
 *   4. 扣减 QUALIFIED 库存
 *   5. 若登录用户是 sales，只能选自己/下级负责的经销商
 *   6. 若登录用户是 dealer，dealer_id 强制为绑定的经销商
 */
package com.dms.surgery.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.ContentDispositionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dms.common.util.TenantContext;
import com.dms.common.util.PagingUtil;
import com.dms.org.controller.SalesOrgResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final com.dms.common.util.DocNoGenerator docNoGenerator;

    @GetMapping
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        UserCtx ctx = loadUserCtx(uid);
        int safePage = PagingUtil.normalizePage(page); int safeSize = PagingUtil.normalizeSize(size); int offset = (safePage - 1) * safeSize;

        Set<Long> allowed = SalesOrgResolver.resolveAccessibleDealerIds(em, tid,
                ctx.role, ctx.salesUserId, ctx.dealerId);

        String where = "WHERE tenant_id = ?1 AND (deleted_at IS NULL)";
        if (allowed != null) {
            if (allowed.isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("total", 0L); data.put("list", Collections.emptyList());
                data.put("page", safePage); data.put("size", safeSize);
                return ApiResponse.ok(data);
            }
            where += " AND dealer_id = ANY(?2)";
        }

        String cntSql = "SELECT COUNT(*) FROM surgery_reports " + where;
        var cntQ = em.createNativeQuery(cntSql);
        cntQ.setParameter(1, tid);
        if (allowed != null) cntQ.setParameter(2, allowed.toArray(new Long[0]));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        String listSql = "SELECT sr.id, sr.code, sr.dealer_id, sr.terminal_id, sr.sales_user_id, " +
                " sr.surgery_date, sr.patient_info, sr.doctor_name, sr.status, sr.remark, sr.created_at, sr.updated_at, sr.created_by, sr.updated_by, " +
                " d.name AS dealer_name, h.name AS terminal_name, sr.attachment_file_id, sr.attachment_name, sr.attachment_url " +
                "FROM surgery_reports sr " +
                "LEFT JOIN dealers d ON d.id = sr.dealer_id " +
                "LEFT JOIN hospitals h ON h.id = sr.terminal_id " +
                where.replace("tenant_id", "sr.tenant_id").replace("deleted_at", "sr.deleted_at").replace("dealer_id", "sr.dealer_id") +
                " ORDER BY sr.updated_at DESC NULLS LAST, sr.id DESC LIMIT ?" +
                (allowed == null ? "2" : "3") + " OFFSET ?" + (allowed == null ? "3" : "4");
        var lq = em.createNativeQuery(listSql, Tuple.class);
        lq.setParameter(1, tid);
        int idx = 2;
        if (allowed != null) { lq.setParameter(idx++, allowed.toArray(new Long[0])); }
        lq.setParameter(idx++, safeSize);
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
            m.put("attachmentFileId", t.get("attachment_file_id"));
            m.put("attachmentName", t.get("attachment_name"));
            m.put("attachmentUrl", t.get("attachment_url"));
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
    @OperationLog(businessType = "surgeryReport", action = OperationAction.CREATE, remark = "手术报台-创建")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        UserCtx ctx = loadUserCtx(uid);

        Long dealerId = toLong(body.get("dealerId"));
        Long terminalId = toLong(body.get("terminalId"));
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
        // v3.8.7 经销商报台不再绑定仓库
        if (surgeryDate == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "手术日期必填");

        // v3.8.7 提交时不再校验经销商-医院授权关系
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
                "INSERT INTO surgery_reports (tenant_id, code, dealer_id, terminal_id, sales_user_id, " +
                "surgery_date, patient_info, doctor_name, status, remark, attachment_file_id, attachment_name, attachment_url, " +
                "created_at, updated_at, created_by) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 'COMPLETED', ?9, ?10, ?11, ?12, now(), now(), ?13) RETURNING id");
        Long attachmentFileId = toLong(body.get("attachmentFileId"));
        String attachmentName = strOr(body.get("attachmentName"), null);
        String attachmentUrl = strOr(body.get("attachmentUrl"), null);
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, dealerId)
                .setParameter(4, terminalId)
                .setParameter(5, ctx.salesUserId)
                .setParameter(6, surgeryDate).setParameter(7, patientInfo).setParameter(8, doctorName)
                .setParameter(9, remark).setParameter(10, attachmentFileId).setParameter(11, attachmentName).setParameter(12, attachmentUrl)
                .setParameter(13, uid);
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
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", reportId);
        res.put("code", code);
        res.put("dealerId", dealerId);
        res.put("terminalId", terminalId);
        res.put("surgeryDate", surgeryDate.toString());
        res.put("lineCount", lines.size());
        res.put("message", "手术报台完成（经销商流向登记，不扣厂家库存），共 " + lines.size() + " 项明细");
        return ApiResponse.ok(res);
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

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT sr.id, sr.code, sr.dealer_id, sr.terminal_id, sr.sales_user_id, " +
                " sr.surgery_date, sr.patient_info, sr.doctor_name, sr.status, sr.remark, " +
                " d.name AS dealer_name, h.name AS terminal_name, sr.attachment_file_id, sr.attachment_name, sr.attachment_url " +
                "FROM surgery_reports sr " +
                "LEFT JOIN dealers d ON d.id = sr.dealer_id " +
                "LEFT JOIN hospitals h ON h.id = sr.terminal_id " +
                "WHERE sr.id = ?1 AND sr.tenant_id = ?2 AND sr.deleted_at IS NULL", Tuple.class)
                .setParameter(1, id).setParameter(2, tid).getResultList();
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到数据");
        }
        Tuple t = rows.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("code", t.get("code"));
        m.put("dealerId", t.get("dealer_id"));
        m.put("dealerName", t.get("dealer_name"));
        m.put("terminalId", t.get("terminal_id"));
        m.put("terminalName", t.get("terminal_name"));
        m.put("salesUserId", t.get("sales_user_id"));
        m.put("surgeryDate", com.dms.common.util.DateFmt.fmt(t.get("surgery_date")));
        m.put("patientInfo", t.get("patient_info"));
        m.put("doctorName", t.get("doctor_name"));
        m.put("status", t.get("status"));
        m.put("remark", t.get("remark"));
        m.put("attachmentFileId", t.get("attachment_file_id"));
        m.put("attachmentName", t.get("attachment_name"));
        m.put("attachmentUrl", t.get("attachment_url"));
        @SuppressWarnings("unchecked")
        List<Tuple> lineRows = em.createNativeQuery(
                "SELECT l.product_id, p.name_cn AS product_name, l.qty, l.batch_no, l.serial_no, l.unit_price " +
                "FROM surgery_report_lines l LEFT JOIN products p ON p.id = l.product_id " +
                "WHERE l.report_id = ?1 ORDER BY l.id", Tuple.class)
                .setParameter(1, id).getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : lineRows) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("productId", l.get("product_id"));
            lm.put("productName", l.get("product_name"));
            lm.put("qty", l.get("qty"));
            lm.put("batchNo", l.get("batch_no"));
            lm.put("serialNo", l.get("serial_no"));
            lm.put("unitPrice", l.get("unit_price"));
            lines.add(lm);
        }
        m.put("lines", lines);
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int aff = em.createNativeQuery("UPDATE surgery_reports SET deleted_at = now() WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "手术报台单不存在");
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        UserCtx ctx = loadUserCtx(uid);

        Set<Long> allowed = SalesOrgResolver.resolveAccessibleDealerIds(em, tid,
                ctx.role, ctx.salesUserId, ctx.dealerId);

        String where = "WHERE tenant_id = ?1 AND (deleted_at IS NULL)";
        if (allowed != null) {
            if (allowed.isEmpty()) {
                List<Map<String, Object>> list = Collections.emptyList();
                String[] headers = {"ID", "报台单号", "经销商ID", "终端ID", "销售用户ID", "手术日期", "患者信息", "医生姓名", "状态", "备注", "附件名", "创建时间", "更新时间", "创建人", "更新人", "经销商名称", "终端名称"};
                String[] fieldNames = {"id", "code", "dealerId", "terminalId", "salesUserId", "surgeryDate", "patientInfo", "doctorName", "status", "remark", "attachmentName", "createdAt", "updatedAt", "createdBy", "updatedBy", "dealerName", "terminalName"};
                byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("手术报台列表.xlsx"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(excelBytes);
            }
            where += " AND dealer_id = ANY(?2)";
        }

        String listSql = "SELECT sr.id, sr.code, sr.dealer_id, sr.terminal_id, sr.sales_user_id, " +
                " sr.surgery_date, sr.patient_info, sr.doctor_name, sr.status, sr.remark, sr.created_at, sr.updated_at, sr.created_by, sr.updated_by, " +
                " d.name AS dealer_name, h.name AS terminal_name, sr.attachment_file_id, sr.attachment_name, sr.attachment_url " +
                "FROM surgery_reports sr " +
                "LEFT JOIN dealers d ON d.id = sr.dealer_id " +
                "LEFT JOIN hospitals h ON h.id = sr.terminal_id " +
                where.replace("tenant_id", "sr.tenant_id").replace("deleted_at", "sr.deleted_at").replace("dealer_id", "sr.dealer_id") +
                " ORDER BY sr.updated_at DESC NULLS LAST, sr.id DESC";
        var lq = em.createNativeQuery(listSql, Tuple.class);
        lq.setParameter(1, tid);
        if (allowed != null) { lq.setParameter(2, allowed.toArray(new Long[0])); }
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
            m.put("attachmentFileId", t.get("attachment_file_id"));
            m.put("attachmentName", t.get("attachment_name"));
            m.put("attachmentUrl", t.get("attachment_url"));
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

        String[] headers = {"ID", "报台单号", "经销商ID", "终端ID", "销售用户ID", "手术日期", "患者信息", "医生姓名", "状态", "备注", "附件名", "创建时间", "更新时间", "创建人", "更新人", "经销商名称", "终端名称"};
        String[] fieldNames = {"id", "code", "dealerId", "terminalId", "salesUserId", "surgeryDate", "patientInfo", "doctorName", "status", "remark", "attachmentName", "createdAt", "updatedAt", "createdBy", "updatedBy", "dealerName", "terminalName"};

        byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("手术报台列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"经销商ID", "终端ID", "手术日期", "患者信息", "医生姓名", "状态"};
        String[] fieldNames = {"dealerId", "terminalId", "surgeryDate", "patientInfo", "doctorName", "status"};
        String[] examples = {"1", "1", "2026-01-31", "示例患者", "张三", "COMPLETED"};
        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("手术报台导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
    @Transactional
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
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
                Long dealerId = toLong(row.get("经销商ID"));
                Long terminalId = toLong(row.get("终端ID"));
                String surgeryDate = ExcelImportUtils.toDateString(row.get("手术日期"));
                String patientInfo = strOr(row.get("患者信息"), null);
                String doctorName = strOr(row.get("医生姓名"), null);
                String status = strOr(row.get("状态"), "COMPLETED");

                if (dealerId == null) {
                    throw new IllegalArgumentException("经销商ID不能为空");
                }
                if (terminalId == null) {
                    throw new IllegalArgumentException("终端ID不能为空");
                }
                if (surgeryDate == null || surgeryDate.trim().isEmpty()) {
                    throw new IllegalArgumentException("手术日期不能为空");
                }
                String sql = "INSERT INTO surgery_reports (code, dealer_id, terminal_id, surgery_date, patient_info, doctor_name, status, tenant_id) " +
                        "VALUES (?1, ?2, ?3, CAST(?4 AS date), ?5, ?6, ?7, ?8)";
                em.createNativeQuery(sql)
                        .setParameter(1, docNoGenerator.next("SURG"))
                        .setParameter(2, dealerId)
                        .setParameter(3, terminalId)
                        .setParameter(4, surgeryDate)
                        .setParameter(5, patientInfo)
                        .setParameter(6, doctorName)
                        .setParameter(7, status)
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
