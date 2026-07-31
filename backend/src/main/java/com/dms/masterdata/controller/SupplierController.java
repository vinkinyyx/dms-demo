/*
 * v3.4.9: 供应商主数据 Controller
 */
package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.ContentDispositionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final EntityManager em;
    private final AuditLogService opLog;

    @GetMapping
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder("WHERE s.tenant_id = ?1 AND s.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (id != null) { where.append(" AND s.id = ?").append(idx++); params.add(id); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.code ILIKE ?").append(idx).append(" OR s.name ILIKE ?").append(idx).append(")");
            params.add("%" + keyword + "%"); idx++;
        }
        if (status != null && !status.isBlank()) { where.append(" AND s.status = ?").append(idx++); params.add(status); }

        var cntQ = em.createNativeQuery("SELECT COUNT(*) FROM suppliers s " + where);
        for (int i = 0; i < params.size(); i++) cntQ.setParameter(i + 1, params.get(i));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        String sql = "SELECT s.id, s.code, s.name, s.contact_person, s.contact_phone, s.address, " +
                "s.bank_account, s.tax_no, s.remark, s.status, s.level, s.created_at, s.updated_at " +
                "FROM suppliers s " + where + " ORDER BY s.id DESC LIMIT ?" + idx + " OFFSET ?" + (idx + 1);
        var q = em.createNativeQuery(sql, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, size);
        q.setParameter(idx + 1, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            m.put("contactPerson", val(t.get("contact_person")));
            m.put("contactPhone", val(t.get("contact_phone")));
            m.put("address", val(t.get("address")));
            m.put("bankAccount", val(t.get("bank_account")));
            m.put("taxNo", val(t.get("tax_no")));
            m.put("level", val(t.get("level")));
            m.put("remark", val(t.get("remark")));
            m.put("status", t.get("status"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", page); data.put("size", size); data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, code, name, contact_person, contact_phone, address, bank_account, tax_no, remark, status, level, created_at, updated_at " +
                "FROM suppliers WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        try {
            Tuple t = (Tuple) q.getSingleResult();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            m.put("contactPerson", val(t.get("contact_person")));
            m.put("contactPhone", val(t.get("contact_phone")));
            m.put("address", val(t.get("address")));
            m.put("bankAccount", val(t.get("bank_account")));
            m.put("taxNo", val(t.get("tax_no")));
            m.put("level", val(t.get("level")));
            m.put("remark", val(t.get("remark")));
            m.put("status", t.get("status"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            return ApiResponse.ok(m);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
    }

    @PostMapping
    @OperationLog(businessType = "supplier", action = OperationAction.CREATE, remark = "供应商-创建")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        if (code == null || code.isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "code 必填");
        if (name == null || name.isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "name 必填");

        var q = em.createNativeQuery(
                "INSERT INTO suppliers (tenant_id, code, name, contact_person, contact_phone, address, bank_account, tax_no, remark, status, level) " +
                "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11) RETURNING id");
        q.setParameter(1, tid).setParameter(2, code).setParameter(3, name)
         .setParameter(4, str(body.get("contactPerson")))
         .setParameter(5, str(body.get("contactPhone")))
         .setParameter(6, str(body.get("address")))
         .setParameter(7, str(body.get("bankAccount")))
         .setParameter(8, str(body.get("taxNo")))
         .setParameter(9, str(body.get("remark")))
         .setParameter(10, str(body.getOrDefault("status", "active")))
         .setParameter(11, str(body.get("level")));
        Long id = ((Number) q.getSingleResult()).longValue();
        opLog.log("supplier", id, "CREATE", "创建供应商 " + name);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id); r.put("code", code); r.put("name", name);
        return ApiResponse.ok(r);
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "supplier", action = OperationAction.UPDATE, remark = "供应商-更新")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        // v3.4.12: 全字段 COALESCE，未提交字段保留原值，避免误清空
        int aff = em.createNativeQuery(
                "UPDATE suppliers SET name = COALESCE(?1, name), contact_person = COALESCE(?2, contact_person), " +
                " contact_phone = COALESCE(?3, contact_phone), address = COALESCE(?4, address), " +
                " bank_account = COALESCE(?5, bank_account), tax_no = COALESCE(?6, tax_no), " +
                " remark = COALESCE(?7, remark), status = COALESCE(?8, status), level = COALESCE(?11, level), updated_at = now() " +
                " WHERE id = ?9 AND tenant_id = ?10")
                .setParameter(1, str(body.get("name")))
                .setParameter(2, str(body.get("contactPerson")))
                .setParameter(3, str(body.get("contactPhone")))
                .setParameter(4, str(body.get("address")))
                .setParameter(5, str(body.get("bankAccount")))
                .setParameter(6, str(body.get("taxNo")))
                .setParameter(7, str(body.get("remark")))
                .setParameter(8, str(body.get("status")))
                .setParameter(9, id).setParameter(10, tid).setParameter(11, str(body.get("level"))).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        opLog.log("supplier", id, "UPDATE", "编辑供应商");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        return ApiResponse.ok(r);
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "supplier", action = OperationAction.DELETE, remark = "供应商-删除")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        // 引用检查：采购订单是否引用该供应商
        long refCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM purchase_orders WHERE supplier_id = ?1 AND tenant_id = ?2 AND deleted_at IS NULL")
                .setParameter(1, id).setParameter(2, tid).getSingleResult()).longValue();
        if (refCount > 0) {
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除供应商：被 " + refCount + " 条采购订单引用");
        }
        int aff = em.createNativeQuery("UPDATE suppliers SET deleted_at = now() WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        opLog.log("supplier", id, "DELETE", "删除供应商");
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT s.id, s.code, s.name, s.contact_person, s.contact_phone, s.address, " +
                "s.bank_account, s.tax_no, s.remark, s.status, s.level, s.created_at, s.updated_at " +
                "FROM suppliers s WHERE s.tenant_id = ?1 AND s.deleted_at IS NULL ORDER BY s.id DESC";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        String[] headers = {"ID", "编码", "名称", "联系人", "电话", "地址", "银行账户", "税号", "备注", "状态", "创建时间", "更新时间"};
        String[] fieldNames = {"id", "code", "name", "contactPerson", "contactPhone", "address", "bankAccount", "taxNo", "remark", "status", "createdAt", "updatedAt"};

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            m.put("contactPerson", val(t.get("contact_person")));
            m.put("contactPhone", val(t.get("contact_phone")));
            m.put("address", val(t.get("address")));
            m.put("bankAccount", val(t.get("bank_account")));
            m.put("taxNo", val(t.get("tax_no")));
            m.put("level", val(t.get("level")));
            m.put("remark", val(t.get("remark")));
            m.put("status", t.get("status"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=suppliers.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"编码", "名称", "联系人", "联系电话", "地址", "状态"};
        String[] fieldNames = {"code", "name", "contactPerson", "contactPhone", "address", "status"};
        String[] examples = {"SUP-001", "示例供应商", "张三", "13800138000", "北京市朝阳区XX路XX号", "active"};

        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("供应商导入模板.xlsx"))
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

        String[] headers = {"编码", "名称", "联系人", "联系电话", "地址", "状态"};
        String[] fieldNames = {"code", "name", "contactPerson", "contactPhone", "address", "status"};

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                com.dms.masterdata.entity.Supplier entity = new com.dms.masterdata.entity.Supplier();
                for (int j = 0; j < headers.length; j++) {
                    Object value = row.get(headers[j]);
                    if (value != null) {
                        setFieldValue(entity, fieldNames[j], value);
                    }
                }
                if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
                    throw new IllegalArgumentException("编码不能为空");
                }
                if (entity.getName() == null || entity.getName().trim().isEmpty()) {
                    throw new IllegalArgumentException("名称不能为空");
                }
                em.persist(entity);
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

    private void setFieldValue(com.dms.masterdata.entity.Supplier entity, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = com.dms.masterdata.entity.Supplier.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<?> type = field.getType();
        if (type == String.class) {
            field.set(entity, String.valueOf(value));
        } else if (type == Long.class || type == long.class) {
            field.set(entity, ((Number) value).longValue());
        } else {
            field.set(entity, value);
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Object val(Object o) { return o == null ? "" : o; }
}
