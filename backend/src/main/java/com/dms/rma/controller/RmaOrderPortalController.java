/*
 * RMA 销退单 v4.3.0 API：/api/rma/orders。
 */
package com.dms.rma.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.rma.entity.RmaOrder;
import com.dms.rma.service.RmaOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rma")
@RequiredArgsConstructor
@Validated
public class RmaOrderPortalController {

    private final RmaOrderService service;

    /** 统一销退单列表（v4.3.0 多出库单 RMA + 历史 orders 红字销退）。 */
    @GetMapping("/orders/unified")
    public ApiResponse<Map<String, Object>> unifiedList(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "30") int size,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long dealerId,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.unifiedList(page, size, status, dealerId, keyword));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResult<RmaOrder>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<RmaOrder> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/orders")
    public ApiResponse<RmaOrder> create(@RequestBody RmaOrder req) {
        return ApiResponse.ok(service.create(req));
    }

    @PostMapping("/orders/{id}/submit")
    public ApiResponse<RmaOrder> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @PostMapping("/orders/{id}/complete")
    public ApiResponse<RmaOrder> complete(@PathVariable Long id) {
        return ApiResponse.ok(service.complete(id));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<RmaOrder> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }
}
