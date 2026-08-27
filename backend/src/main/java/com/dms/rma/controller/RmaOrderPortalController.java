/*
 * RMA 销退单 v4.3.0 API：/api/rma/orders。
 */
package com.dms.rma.controller;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.order.service.SalesReturnService;
import com.dms.rma.entity.RmaOrder;
import com.dms.rma.service.RmaOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rma/orders")
@RequiredArgsConstructor
@Validated
public class RmaOrderPortalController {

    private final RmaOrderService service;
    private final ApprovalService approvalService;
    private final SalesReturnService salesReturnService;

    /** 统一销退单列表（v4.3.0 多出库单 RMA + 历史 orders 红字销退）。 */
    @GetMapping("/unified")
    public ApiResponse<Map<String, Object>> unifiedList(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "30") int size,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long dealerId,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.unifiedList(page, size, status, dealerId, keyword));
    }

    @GetMapping
    public ApiResponse<PageResult<RmaOrder>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<RmaOrder> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<RmaOrder> create(@RequestBody RmaOrder req) {
        return ApiResponse.ok(service.create(req));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<RmaOrder> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<RmaOrder> complete(@PathVariable Long id) {
        return ApiResponse.ok(service.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<RmaOrder> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }

    // ===== 统一列表（unified，行 id 形如 r123 / l456）的审批操作端点 =====

    @PostMapping("/unified/{uid}/submit")
    public ApiResponse<Map<String, Object>> unifiedSubmit(@PathVariable String uid) {
        return dispatchAction(uid, "submit");
    }

    @PostMapping("/unified/{uid}/approve")
    public ApiResponse<Map<String, Object>> unifiedApprove(@PathVariable String uid) {
        return dispatchAction(uid, "approve");
    }

    @PostMapping("/unified/{uid}/reject")
    public ApiResponse<Map<String, Object>> unifiedReject(@PathVariable String uid) {
        return dispatchAction(uid, "reject");
    }

    @PostMapping("/unified/{uid}/cancel")
    public ApiResponse<Map<String, Object>> unifiedCancel(@PathVariable String uid) {
        return dispatchAction(uid, "cancel");
    }

    /**
     * r 前缀 = v4.3.0 rma_orders：submit 走 RmaOrderService.submit（创建审批实例），
     * approve/reject 走审批服务按业务类型 RMA_ORDER 驱动回调；l 前缀 = 历史 orders 红字销退，转发 SalesReturnService。
     */
    @SuppressWarnings("unchecked")
    private ApiResponse<Map<String, Object>> dispatchAction(String uid, String action) {
        if (uid == null || uid.isBlank()) {
            return ApiResponse.fail(40001, "缺少单据标识");
        }
        char kind = uid.charAt(0);
        long numericId;
        if (kind == 'r' || kind == 'l') {
            try { numericId = Long.parseLong(uid.substring(1)); }
            catch (NumberFormatException e) { return ApiResponse.fail(40001, "非法单据标识: " + uid); }
        } else {
            // 纯数字兼容：按 RMA 处理
            try { numericId = Long.parseLong(uid); }
            catch (NumberFormatException e) { return ApiResponse.fail(40001, "非法单据标识: " + uid); }
            kind = 'r';
        }

        if (kind == 'l') {
            return switch (action) {
                case "submit" -> salesReturnService.submit(numericId);
                case "approve" -> salesReturnService.approve(numericId);
                case "reject" -> salesReturnService.reject(numericId);
                default -> salesReturnService.cancel(numericId);
            };
        }

        // RMA
        switch (action) {
            case "submit" -> {
                RmaOrder order = service.submit(numericId);
                return ApiResponse.ok(Map.of("id", order.getId(), "code", order.getCode() == null ? "" : order.getCode(),
                        "status", order.getStatus() == null ? "" : order.getStatus()));
            }
            case "approve" -> {
                ApprovalInstance inst = approvalService.approveBusiness("RMA_ORDER", numericId, null);
                return ApiResponse.ok(Map.of("id", numericId, "status", approvalStatus(inst)));
            }
            case "reject" -> {
                ApprovalInstance inst = approvalService.rejectBusiness("RMA_ORDER", numericId, null);
                return ApiResponse.ok(Map.of("id", numericId, "status", approvalStatus(inst)));
            }
            default -> {
                RmaOrder order = service.cancel(numericId);
                return ApiResponse.ok(Map.of("id", order.getId(), "status", order.getStatus() == null ? "" : order.getStatus()));
            }
        }
    }

    private String approvalStatus(ApprovalInstance inst) {
        if (inst == null || inst.getStatus() == null) return "PENDING_APPROVAL";
        String s = inst.getStatus().name();
        return switch (s) {
            case "APPROVED", "AUTO_APPROVED" -> "COMPLETED";
            case "REJECTED" -> "REJECTED";
            default -> "PENDING_APPROVAL";
        };
    }
}
