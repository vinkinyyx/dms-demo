/*
 * 收货子单控制器 (v3.7.4)
 * 
 * 端点:
 *   POST /api/receipts/{id}/batches          -> 创建子单 (DRAFT)
 *   PUT  /api/receipt-batches/{bid}          -> 更新子单明细 (保存草稿)
 *   POST /api/receipt-batches/{bid}/confirm  -> 确认收货 (写库存)
 *   POST /api/receipt-batches/{bid}/cancel   -> 取消本次
 *   POST /api/receipts/{id}/cancel-remaining -> 取消剩余收货
 */
package com.dms.inventory.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.inventory.service.ReceiptBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReceiptBatchController {

    private final ReceiptBatchService service;

    /** 创建子单 (在父单下方) */
    @PostMapping("/api/receipts/{id}/batches")
    @OperationLog(businessType = "receipt", action = OperationAction.CREATE, remark = "收货子单-创建")
    public ApiResponse<Map<String, Object>> createBatch(@PathVariable Long id) {
        return ApiResponse.ok(service.createBatch(id));
    }

    /** 更新子单明细 (仅 DRAFT 可改) */
    @PutMapping("/api/receipt-batches/{bid}")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货子单-更新明细")
    public ApiResponse<Map<String, Object>> updateBatch(@PathVariable Long bid, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.getOrDefault("lines", List.of());
        return ApiResponse.ok(service.updateBatchLines(bid, lines));
    }

    /** 确认收货 (核心业务, 写 inventory / stock_serials) */
    @PostMapping("/api/receipt-batches/{bid}/confirm")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货子单-确认收货")
    public ApiResponse<Map<String, Object>> confirmBatch(@PathVariable Long bid) {
        return ApiResponse.ok(service.confirmBatch(bid));
    }

    /** 取消本次 (取消当前 DRAFT 子单) */
    @PostMapping("/api/receipt-batches/{bid}/cancel")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货子单-取消本次")
    public ApiResponse<Map<String, Object>> cancelBatch(@PathVariable Long bid, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        return ApiResponse.ok(service.cancelBatch(bid, reason));
    }

    /** 取消剩余 (父单不再允许新子单) */
    @PostMapping("/api/receipts/{id}/cancel-remaining")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货入库-取消剩余")
    public ApiResponse<Map<String, Object>> cancelRemaining(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        return ApiResponse.ok(service.cancelRemaining(id, reason));
    }
}
