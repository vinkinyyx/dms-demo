/*
 * 收货单控制器：/api/receipts
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.inventory.dto.ReceiptConfirmRequest;
import com.dms.inventory.entity.Receipt;
import com.dms.inventory.service.ReceiptService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
@Validated
public class ReceiptController {

    private final ReceiptService service;
    private final EntityManager em;

    // GET 已移到 BizDocListController（v3.4.6 增强字段）

    @PostMapping
    public ApiResponse<Receipt> create(@RequestBody Receipt receipt) {
        return ApiResponse.ok(service.create(receipt));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Receipt> confirm(@PathVariable Long id, @RequestBody ReceiptConfirmRequest request) {
        return ApiResponse.ok(service.confirm(id, request.getLines()));
    }

    /**
     * 异常收货整单撤销（US-B-14）
     * 将收货单状态改为 CANCELLED，回滚已入库的库存
     */
    @PostMapping("/{id}/cancel")
    @Transactional
    public ApiResponse<Map<String, Object>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "异常收货撤销" : String.valueOf(body.getOrDefault("reason", "异常收货撤销"));
        try {
            // 更新收货单状态
            var upd = em.createNativeQuery(
                    "UPDATE receipts SET status = 'CANCELLED' " +
                    "WHERE id = ?1 AND tenant_id = ?2 AND status != 'CANCELLED'");
            upd.setParameter(1, id).setParameter(2, TenantContext.getTenantId());
            int affected = upd.executeUpdate();
            if (affected == 0) return ApiResponse.fail(40404, "收货单不存在或已撤销");

            // 记录审计
            var audit = em.createNativeQuery(
                    "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, ip, at_time) " +
                    "VALUES (?1, ?2, 'RECEIPT_CANCEL', 'receipt', ?3, '127.0.0.1', now())");
            audit.setParameter(1, TenantContext.getTenantId());
            audit.setParameter(2, TenantContext.getUserId());
            audit.setParameter(3, String.valueOf(id));
            audit.executeUpdate();

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("receiptId", id);
            res.put("cancelled", true);
            res.put("reason", reason);
            return ApiResponse.ok(res);
        } catch (Exception e) {
            return ApiResponse.fail(50000, "撤销失败：" + e.getMessage());
        }
    }
}
