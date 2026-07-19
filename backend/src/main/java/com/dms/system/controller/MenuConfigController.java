/*
 * 菜单分组配置 API（v3.4.15）
 * - GET  /api/menu-configs           返回当前租户的菜单分组覆盖配置（menu_key -> group/sort/visible）
 * - POST /api/menu-configs/upsert     批量保存（管理员在后台调整菜单所属分组/排序/显隐）
 * - DELETE /api/menu-configs/{menuKey} 删除某菜单的覆盖（回退到前端默认分组）
 * 前端 workspace 启动时拉取本配置，覆盖硬编码 MENU_GROUPS 里各菜单的分组归属与排序。
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
@RequestMapping("/api/menu-configs")
@RequiredArgsConstructor
public class MenuConfigController {

    private final EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> list() {
        var q = em.createNativeQuery(
                "SELECT id, menu_key, group_name, label, icon, sort_order, visible " +
                "FROM menu_configs WHERE tenant_id = ?1 ORDER BY group_name, sort_order, id",
                Tuple.class);
        q.setParameter(1, TenantContext.getTenantId());
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("menuKey", t.get("menu_key"));
            m.put("group", t.get("group_name"));
            m.put("label", t.get("label"));
            m.put("icon", t.get("icon"));
            m.put("sortOrder", t.get("sort_order"));
            m.put("visible", t.get("visible"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @PostMapping("/upsert")
    @Transactional
    public ApiResponse<Map<String, Object>> upsert(@RequestBody List<Map<String, Object>> items) {
        UUID tid = TenantContext.getTenantId();
        int n = 0;
        for (Map<String, Object> it : items) {
            String menuKey = String.valueOf(it.get("menuKey"));
            if (menuKey == null || menuKey.isBlank() || "null".equals(menuKey)) continue;
            String group = str(it.get("group"));
            em.createNativeQuery(
                    "INSERT INTO menu_configs (tenant_id, menu_key, group_name, label, icon, sort_order, visible, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, now(), now()) " +
                    "ON CONFLICT (tenant_id, menu_key) DO UPDATE SET " +
                    "group_name = EXCLUDED.group_name, label = EXCLUDED.label, icon = EXCLUDED.icon, " +
                    "sort_order = EXCLUDED.sort_order, visible = EXCLUDED.visible, updated_at = now()")
                .setParameter(1, tid)
                .setParameter(2, menuKey)
                .setParameter(3, group == null ? "未分组" : group)
                .setParameter(4, it.get("label"))
                .setParameter(5, it.get("icon"))
                .setParameter(6, toInt(it.getOrDefault("sortOrder", 100)))
                .setParameter(7, toBool(it.getOrDefault("visible", true)))
                .executeUpdate();
            n++;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("saved", n);
        return ApiResponse.ok(res);
    }

    @DeleteMapping("/{menuKey}")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(@PathVariable String menuKey) {
        int n = em.createNativeQuery("DELETE FROM menu_configs WHERE tenant_id = ?1 AND menu_key = ?2")
                .setParameter(1, TenantContext.getTenantId()).setParameter(2, menuKey).executeUpdate();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deleted", n);
        return ApiResponse.ok(res);
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private Boolean toBool(Object o) {
        if (o == null) return true;
        if (o instanceof Boolean) return (Boolean) o;
        return !"false".equalsIgnoreCase(String.valueOf(o));
    }
    private Integer toInt(Object o) {
        if (o == null) return 100;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 100; }
    }
}
