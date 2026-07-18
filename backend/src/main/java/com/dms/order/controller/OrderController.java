/*
 * 订单 REST 控制器：/api/orders 及状态动作。
 */
package com.dms.order.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.order.dto.OrderCreateRequest;
import com.dms.order.dto.OrderDTO;
import com.dms.order.entity.Order;
import com.dms.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService service;

    @GetMapping
    public ApiResponse<PageResult<Order>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDTO> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<OrderDTO> create(@RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(service.createOrder(request));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Order> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Order> approve(@PathVariable Long id) {
        return ApiResponse.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Order> reject(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(service.reject(id, reason));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Order> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }

    @PostMapping("/{id}/split")
    public ApiResponse<Order> split(@PathVariable Long id) {
        return ApiResponse.ok(service.split(id));
    }
}
