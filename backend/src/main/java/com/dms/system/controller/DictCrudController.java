/*
 * 数据字典 CRUD API
 * - GET    /api/dicts/types              字典分类列表
 * - GET    /api/dicts/{typeCode}/items   某字典的所有条目
 * - POST   /api/dicts/{typeCode}/items   新增条目
 * - PUT    /api/dicts/items/{id}         修改条目
 * - DELETE /api/dicts/items/{id}         删除条目
 * - POST   /api/dicts/types              新增字典类型
 */
package com.dms.system.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
public class DictCrudController {

    private final EntityManager em;

    @GetMapping("/types")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> types() {
        var q = em.createNativeQuery(
                "SELECT id, code, name FROM dict_types WHERE tenant_id = ?1 OR tenant_id IS NULL ORDER BY code",
                Tuple.class);
        q.setParameter(1, TenantContext.getTenantId());
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @PostMapping("/types")
    @Transactional
    @OperationLog(businessType = "dict", action = OperationAction.CREATE, remark = "字典类型-新增")
    public ApiResponse<Map<String, Object>> createType(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null || String.valueOf(body.getOrDefault("code", "")).isBlank()
                || String.valueOf(body.getOrDefault("name", "")).isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "code, name must not be empty");
        }
        em.createNativeQuery(
                "INSERT INTO dict_types (tenant_id, code, name) VALUES (?1, ?2, ?3)")
            .setParameter(1, TenantContext.getTenantId())
            .setParameter(2, body.get("code"))
            .setParameter(3, body.get("name"))
            .executeUpdate();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", body.get("code"));
        return ApiResponse.ok(res);
    }

    @GetMapping("/{typeCode}/items")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> items(@PathVariable String typeCode) {
        // v3.7.2 fix: 优先当前租户字典;若无则回退 SYSTEM 模板租户;并 DISTINCT 避免重复
        java.util.UUID tid = com.dms.common.util.TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, code, name, seq FROM (" +
                "  SELECT DISTINCT ON (di.code) di.id, di.code, di.name, di.seq FROM dict_items di " +
                "  JOIN dict_types dt ON dt.id = di.type_id " +
                "  WHERE dt.code = ?1 AND (dt.tenant_id = ?2 OR dt.tenant_id IS NULL OR dt.tenant_id = '00000000-0000-0000-0000-000000000000') " +
                "  ORDER BY di.code, CASE WHEN dt.tenant_id = ?2 THEN 0 ELSE 1 END, di.seq, di.id" +
                ") x ORDER BY seq, id", Tuple.class);
        q.setParameter(1, typeCode);
        q.setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("itemCode", t.get("code"));
            m.put("label", t.get("name"));
            m.put("value", t.get("code"));
            m.put("sortOrder", t.get("seq"));
            m.put("status", "active");
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @PostMapping("/{typeCode}/items")
    @Transactional
    @OperationLog(businessType = "dict", action = OperationAction.CREATE, remark = "字典条目-新增")
    public ApiResponse<Map<String, Object>> addItem(@PathVariable String typeCode, @RequestBody Map<String, Object> body) {
        try {
            em.createNativeQuery(
                    "INSERT INTO dict_items (type_id, code, name, seq) " +
                    "VALUES ((SELECT id FROM dict_types WHERE code = ?1 LIMIT 1), ?2, ?3, ?4)")
                .setParameter(1, typeCode)
                .setParameter(2, body.get("code"))
                .setParameter(3, body.get("name"))
                .setParameter(4, body.getOrDefault("seq", 100))
                .executeUpdate();
            return ApiResponse.ok(Collections.singletonMap("added", true));
        } catch (Exception e) {
            return ApiResponse.fail(50000, "新增失败：" + e.getMessage());
        }
    }

    @PutMapping("/items/{id}")
    @Transactional
    @OperationLog(businessType = "dict", action = OperationAction.UPDATE, remark = "字典条目-修改")
    public ApiResponse<Map<String, Object>> updateItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int n = em.createNativeQuery(
                "UPDATE dict_items SET code = ?1, name = ?2, seq = ?3 WHERE id = ?4")
            .setParameter(1, body.get("code"))
            .setParameter(2, body.get("name"))
            .setParameter(3, body.getOrDefault("seq", 100))
            .setParameter(4, id).executeUpdate();
        return ApiResponse.ok(Collections.singletonMap("updated", n));
    }

    @DeleteMapping("/items/{id}")
    @Transactional
    @OperationLog(businessType = "dict", action = OperationAction.DELETE, remark = "字典条目-删除")
    public ApiResponse<Map<String, Object>> deleteItem(@PathVariable Long id) {
        int n = em.createNativeQuery("DELETE FROM dict_items WHERE id = ?1").setParameter(1, id).executeUpdate();
        return ApiResponse.ok(Collections.singletonMap("deleted", n));
    }
}
