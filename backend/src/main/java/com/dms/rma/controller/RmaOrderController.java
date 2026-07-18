/*
 * RMA 订单控制器：/api/rma-orders
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

@RestController
@RequestMapping("/api/rma-orders")
@RequiredArgsConstructor
@Validated
public class RmaOrderController {

    private final RmaOrderService service;

    @GetMapping
    public ApiResponse<PageResult<RmaOrder>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @PostMapping
    public ApiResponse<RmaOrder> create(@RequestBody RmaOrder req) {
        return ApiResponse.ok(service.create(req));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<RmaOrder> complete(@PathVariable Long id) {
        return ApiResponse.ok(service.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<RmaOrder> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }
}
