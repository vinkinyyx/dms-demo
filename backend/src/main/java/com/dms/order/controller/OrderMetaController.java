/*
 * 销售订单/采购订单 元数据接口
 * - 根据状态返回允许的操作按钮（用于前端状态驱动 UI）
 * - 支持销售订单 (order) 与采购订单 (purchase_order) 两种 formKey
 */
package com.dms.order.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderMetaController {

    private final EntityManager em;

    /**
     * 根据订单状态返回允许的操作
     * 前端使用：新建列表页时，为每一行按 status 决定显示哪些按钮
     */
    @GetMapping("/{id}/allowed-actions")
    public ApiResponse<List<Map<String, Object>>> allowedActions(@PathVariable Long id) {
        String status = "";
        try {
            var q = em.createNativeQuery("SELECT status FROM orders WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, id).setParameter(2, TenantContext.getTenantId());
            status = String.valueOf(q.getSingleResult());
        } catch (Exception ignored) {}
        return ApiResponse.ok(salesOrderActions(status));
    }

    /**
     * 批量返回：给定状态字符串直接返回允许的操作（前端不需要每行发一个请求）
     */
    @GetMapping("/actions-for-status")
    public ApiResponse<List<Map<String, Object>>> actionsForStatus(@RequestParam String status) {
        return ApiResponse.ok(salesOrderActions(status));
    }

    /**
     * 销售订单的状态机
     * DRAFT     → 编辑 / 提交审批 / 取消
     * SUBMITTED → 审批通过 / 驳回 / 取消
     * APPROVED  → 出库发货 / 红冲
     * COMPLETED → 无
     * REJECTED  → 无
     * CANCELLED → 无
     */
    static List<Map<String, Object>> salesOrderActions(String status) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (status == null) status = "";
        String s = status.toUpperCase();
        switch (s) {
            case "DRAFT":
                actions.add(mk("edit",    "编辑",     "primary", "PUT",  ""));
                actions.add(mk("submit",  "提交审批", "warn",    "POST", "/submit"));
                actions.add(mk("cancel",  "取消",     "danger",  "POST", "/cancel"));
                break;
            case "SUBMITTED":
                actions.add(mk("approve", "审批通过", "success", "POST", "/approve"));
                actions.add(mk("reject",  "驳回",     "danger",  "POST", "/reject"));
                actions.add(mk("cancel",  "取消",     "default", "POST", "/cancel"));
                break;
            case "APPROVED":
                actions.add(mk("ship",    "出库发货", "success", "POST", "/ship"));
                break;
            default:
                // COMPLETED / REJECTED / CANCELLED / COMPLETED / SHIPPED
                break;
        }
        return actions;
    }

    private static Map<String, Object> mk(String key, String label, String type, String method, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("type", type);
        m.put("method", method);
        m.put("path", path);
        return m;
    }
}
