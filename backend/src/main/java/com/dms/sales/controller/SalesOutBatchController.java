package com.dms.sales.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.sales.service.SalesOutBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 销售出库子单（发货批次）控制器，对齐收货入库 ReceiptBatchController。
 *  - POST   /api/sales-outs/{id}/batches            创建发货子单
 *  - PUT    /api/sales-out-batches/{bid}            保存子单明细
 *  - POST   /api/sales-out-batches/{bid}/confirm    确认发货
 *  - POST   /api/sales-out-batches/{bid}/cancel     取消本次（仅 DRAFT）
 *  - POST   /api/sales-outs/{id}/cancel-remaining   取消剩余待发
 */
@RestController
@RequiredArgsConstructor
public class SalesOutBatchController {

    private final SalesOutBatchService salesOutBatchService;

    @PostMapping("/api/sales-outs/{id}/batches")
    @OperationLog(businessType = "salesOut", action = OperationAction.CREATE, remark = "销售出库-创建发货子单")
    public ApiResponse<Map<String, Object>> createBatch(@PathVariable Long id) {
        return ApiResponse.ok(salesOutBatchService.createBatch(id));
    }

    @PutMapping("/api/sales-out-batches/{bid}")
    @OperationLog(businessType = "salesOut", action = OperationAction.UPDATE, remark = "销售出库-保存发货明细")
    public ApiResponse<Map<String, Object>> updateBatch(@PathVariable Long bid,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = body == null ? List.of() : (List<Map<String, Object>>) body.get("lines");
        return ApiResponse.ok(salesOutBatchService.updateBatchLines(bid, lines));
    }

    @PostMapping("/api/sales-out-batches/{bid}/confirm")
    @OperationLog(businessType = "salesOut", action = OperationAction.APPROVE, remark = "销售出库-确认发货")
    public ApiResponse<Map<String, Object>> confirmBatch(@PathVariable Long bid) {
        return ApiResponse.ok(salesOutBatchService.confirmBatch(bid));
    }

    @PostMapping("/api/sales-out-batches/{bid}/cancel")
    @OperationLog(businessType = "salesOut", action = OperationAction.CANCEL, remark = "销售出库-取消本次发货")
    public ApiResponse<Map<String, Object>> cancelBatch(@PathVariable Long bid,
                                                        @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(salesOutBatchService.cancelBatch(bid, reason));
    }

    @PostMapping("/api/sales-outs/{id}/cancel-remaining")
    @OperationLog(businessType = "salesOut", action = OperationAction.CANCEL, remark = "销售出库-取消剩余待发")
    public ApiResponse<Map<String, Object>> cancelRemaining(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(salesOutBatchService.cancelRemaining(id, reason));
    }
}