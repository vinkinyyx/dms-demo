/*
 * 订单 REST 控制器：/api/orders 及状态动作。
 */
package com.dms.order.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.execution.service.AutoDocGenerator;
import com.dms.execution.service.OperationLogService;
import com.dms.order.dto.OrderCreateRequest;
import com.dms.order.dto.OrderDTO;
import com.dms.order.entity.Order;
import com.dms.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService service;
    private final AutoDocGenerator autoDocGenerator;
    private final OperationLogService opLog;

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
        OrderDTO dto = service.createOrder(request);
        try { if (dto != null && dto.getOrder() != null) opLog.log("order", dto.getOrder().getId(), "CREATE", "创建销售订单"); } catch (Exception ignored) {}
        return ApiResponse.ok(dto);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Order> submit(@PathVariable Long id) {
        opLog.log("order", id, "SUBMIT", "提交审批");
        return ApiResponse.ok(service.submit(id));
    }

    /**
     * v3.4 增强：审批通过后自动生成销售出库草稿单
     */
    @PostMapping("/{id}/approve")
    @Transactional
    public ApiResponse<Order> approve(@PathVariable Long id) {
        Order order = service.approve(id);
        opLog.log("order", id, "APPROVE", "审批通过");
        try {
            Long soId = autoDocGenerator.createSalesOutForOrder(id);
            log.info("订单 {} 审批通过，自动生成销售出库单 {}", id, soId);
        } catch (Exception e) {
            log.warn("订单 {} 审批通过，但自动生成销售出库失败: {}", id, e.getMessage());
        }
        return ApiResponse.ok(order);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Order> reject(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        opLog.log("order", id, "REJECT", "驳回" + (reason != null ? "：" + reason : ""));
        return ApiResponse.ok(service.reject(id, reason));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Order> cancel(@PathVariable Long id) {
        opLog.log("order", id, "CANCEL", "取消订单");
        return ApiResponse.ok(service.cancel(id));
    }

    @PostMapping("/{id}/split")
    public ApiResponse<Order> split(@PathVariable Long id) {
        return ApiResponse.ok(service.split(id));
    }
}
