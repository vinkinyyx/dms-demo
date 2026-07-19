/*
 * v3.4.9: 供应商主数据 Controller
 */
package com.dms.masterdata.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.OperationLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final EntityManager em;
    private final OperationLogService opLog;

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
                "s.bank_account, s.tax_no, s.remark, s.status, s.created_at, s.updated_at " +
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
                "SELECT id, code, name, contact_person, contact_phone, address, bank_account, tax_no, remark, status, created_at, updated_at " +
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
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        if (code == null || code.isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "code 必填");
        if (name == null || name.isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "name 必填");

        var q = em.createNativeQuery(
                "INSERT INTO suppliers (tenant_id, code, name, contact_person, contact_phone, address, bank_account, tax_no, remark, status) " +
                "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10) RETURNING id");
        q.setParameter(1, tid).setParameter(2, code).setParameter(3, name)
         .setParameter(4, str(body.get("contactPerson")))
         .setParameter(5, str(body.get("contactPhone")))
         .setParameter(6, str(body.get("address")))
         .setParameter(7, str(body.get("bankAccount")))
         .setParameter(8, str(body.get("taxNo")))
         .setParameter(9, str(body.get("remark")))
         .setParameter(10, str(body.getOrDefault("status", "active")));
        Long id = ((Number) q.getSingleResult()).longValue();
        opLog.log("supplier", id, "CREATE", "创建供应商 " + name);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id); r.put("code", code); r.put("name", name);
        return ApiResponse.ok(r);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        // v3.4.12: 全字段 COALESCE，未提交字段保留原值，避免误清空
        int aff = em.createNativeQuery(
                "UPDATE suppliers SET name = COALESCE(?1, name), contact_person = COALESCE(?2, contact_person), " +
                " contact_phone = COALESCE(?3, contact_phone), address = COALESCE(?4, address), " +
                " bank_account = COALESCE(?5, bank_account), tax_no = COALESCE(?6, tax_no), " +
                " remark = COALESCE(?7, remark), status = COALESCE(?8, status), updated_at = now() " +
                " WHERE id = ?9 AND tenant_id = ?10")
                .setParameter(1, str(body.get("name")))
                .setParameter(2, str(body.get("contactPerson")))
                .setParameter(3, str(body.get("contactPhone")))
                .setParameter(4, str(body.get("address")))
                .setParameter(5, str(body.get("bankAccount")))
                .setParameter(6, str(body.get("taxNo")))
                .setParameter(7, str(body.get("remark")))
                .setParameter(8, str(body.get("status")))
                .setParameter(9, id).setParameter(10, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        opLog.log("supplier", id, "UPDATE", "编辑供应商");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        return ApiResponse.ok(r);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Object val(Object o) { return o == null ? "" : o; }
}
