/*
 * 低代码字段配置 API
 * - GET  /api/form-configs/{formKey}            返回该表单的所有字段配置
 * - POST /api/form-configs/{formKey}/upsert     批量更新（管理员用）
 * - POST /api/form-configs/{formKey}/add-custom 新增自定义字段（存 extra JSONB）
 * - DELETE /api/form-configs/{id}               删除字段配置
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/form-configs")
@RequiredArgsConstructor
public class FormConfigController {

    private final EntityManager em;

    /** 按 formKey 拉取字段配置（前端渲染表单/列表/详情用） */
    @GetMapping("/{formKey}")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> get(@PathVariable String formKey) {
        var q = em.createNativeQuery(
                "SELECT id, form_key, field_key, field_label, field_type, is_native, required, " +
                "show_in_list, show_in_form, show_in_detail, default_value, options_json, picker_resource, " +
                "placeholder, field_group, sort_order " +
                "FROM form_configs WHERE tenant_id = ?1 AND form_key = ?2 ORDER BY sort_order, id",
                Tuple.class);
        q.setParameter(1, TenantContext.getTenantId()).setParameter(2, formKey);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("formKey", t.get("form_key"));
            m.put("fieldKey", t.get("field_key"));
            m.put("fieldLabel", t.get("field_label"));
            m.put("fieldType", t.get("field_type"));
            m.put("isNative", t.get("is_native"));
            m.put("required", t.get("required"));
            m.put("showInList", t.get("show_in_list"));
            m.put("showInForm", t.get("show_in_form"));
            m.put("showInDetail", t.get("show_in_detail"));
            m.put("defaultValue", t.get("default_value"));
            m.put("optionsJson", t.get("options_json"));
            m.put("pickerResource", t.get("picker_resource"));
            m.put("placeholder", t.get("placeholder"));
            m.put("group", t.get("field_group"));
            m.put("sortOrder", t.get("sort_order"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    /** 批量更新字段属性 */
    @PostMapping("/{formKey}/upsert")
    @Transactional
    public ApiResponse<Map<String, Object>> upsert(@PathVariable String formKey, @RequestBody List<Map<String, Object>> fields) {
        UUID tid = TenantContext.getTenantId();
        int updated = 0, inserted = 0;
        for (Map<String, Object> f : fields) {
            String fieldKey = String.valueOf(f.get("fieldKey"));
            var exist = em.createNativeQuery(
                    "SELECT id FROM form_configs WHERE tenant_id = ?1 AND form_key = ?2 AND field_key = ?3");
            exist.setParameter(1, tid).setParameter(2, formKey).setParameter(3, fieldKey);
            List<?> rs = exist.getResultList();
            if (!rs.isEmpty()) {
                Long id = ((Number) rs.get(0)).longValue();
                em.createNativeQuery(
                        "UPDATE form_configs SET field_label=?1, field_type=?2, required=?3, show_in_list=?4, " +
                        "show_in_form=?5, show_in_detail=?6, default_value=?7, options_json=?8, picker_resource=?9, " +
                        "placeholder=?10, field_group=?11, sort_order=?12, updated_at=now() WHERE id=?13")
                    .setParameter(1, f.get("fieldLabel"))
                    .setParameter(2, f.getOrDefault("fieldType", "text"))
                    .setParameter(3, toBool(f.get("required")))
                    .setParameter(4, toBool(f.get("showInList")))
                    .setParameter(5, toBool(f.get("showInForm")))
                    .setParameter(6, toBool(f.get("showInDetail")))
                    .setParameter(7, f.get("defaultValue"))
                    .setParameter(8, f.get("optionsJson"))
                    .setParameter(9, f.get("pickerResource"))
                    .setParameter(10, f.get("placeholder"))
                    .setParameter(11, f.get("group"))
                    .setParameter(12, toInt(f.getOrDefault("sortOrder", 100)))
                    .setParameter(13, id)
                    .executeUpdate();
                updated++;
            } else {
                em.createNativeQuery(
                        "INSERT INTO form_configs (tenant_id, form_key, field_key, field_label, field_type, is_native, " +
                        "required, show_in_list, show_in_form, show_in_detail, default_value, options_json, picker_resource, " +
                        "placeholder, field_group, sort_order, created_at, updated_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, now(), now())")
                    .setParameter(1, tid).setParameter(2, formKey).setParameter(3, fieldKey)
                    .setParameter(4, f.get("fieldLabel"))
                    .setParameter(5, f.getOrDefault("fieldType", "text"))
                    .setParameter(6, toBool(f.getOrDefault("isNative", false)))
                    .setParameter(7, toBool(f.get("required")))
                    .setParameter(8, toBool(f.get("showInList")))
                    .setParameter(9, toBool(f.get("showInForm")))
                    .setParameter(10, toBool(f.get("showInDetail")))
                    .setParameter(11, f.get("defaultValue"))
                    .setParameter(12, f.get("optionsJson"))
                    .setParameter(13, f.get("pickerResource"))
                    .setParameter(14, f.get("placeholder"))
                    .setParameter(15, f.get("group"))
                    .setParameter(16, toInt(f.getOrDefault("sortOrder", 100)))
                    .executeUpdate();
                inserted++;
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("updated", updated);
        res.put("inserted", inserted);
        return ApiResponse.ok(res);
    }

    /** 删除字段配置 */
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        int n = em.createNativeQuery("DELETE FROM form_configs WHERE id = ?1 AND tenant_id = ?2")
            .setParameter(1, id).setParameter(2, TenantContext.getTenantId()).executeUpdate();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deleted", n);
        return ApiResponse.ok(res);
    }

    /** 列出所有表单 key（管理员选表单） */
    @GetMapping("/forms")
    public ApiResponse<List<Map<String, Object>>> forms() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(f("order",          "销售订单"));
        list.add(f("purchase_order", "采购订单"));
        list.add(f("product",        "产品"));
        list.add(f("dealer",         "经销商"));
        list.add(f("warehouse",      "仓库"));
        list.add(f("hospital",       "医院/终端"));
        return ApiResponse.ok(list);
    }

    private Map<String, Object> f(String k, String l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", k); m.put("label", l);
        return m;
    }
    private Boolean toBool(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        return "true".equalsIgnoreCase(String.valueOf(o));
    }
    private Integer toInt(Object o) {
        if (o == null) return 100;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 100; }
    }
}
