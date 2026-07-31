/*
 * 销售出库控制器：/api/sales-outs
 */
package com.dms.sales.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.sales.dto.SalesOutCancelFullRequest;
import com.dms.sales.dto.SalesOutCancelPartialRequest;
import com.dms.sales.dto.SalesOutCreateRequest;
import com.dms.sales.dto.SalesOutPartialShipRequest;
import com.dms.sales.entity.SalesOut;
import com.dms.sales.service.SalesOutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sales-outs")
@RequiredArgsConstructor
@Validated
public class SalesOutController {

    private final SalesOutService service;

    // GET 已移到 BizDocListController（v3.4.6 增强字段）

    @PostMapping
    @OperationLog(businessType = "salesOut", action = OperationAction.CREATE, remark = "销售出库-创建")
    public ApiResponse<SalesOut> create(@RequestBody SalesOutCreateRequest request) {
        return ApiResponse.ok(service.create(request.getSalesOut(), request.getLines()));
    }

    @PostMapping("/{id}/red-cancel")
    @OperationLog(businessType = "salesOut", action = OperationAction.UPDATE, remark = "销售出库-红字取消")
    public ApiResponse<SalesOut> redCancel(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(service.redCancel(id, reason));
    }

    /**
     * v3.7.3 部分出库（SAP SD VL01N 风格）：每次提交一批行项目扣库存
     */
    @PostMapping("/{id}/partial-ship")
    @OperationLog(businessType = "salesOut", action = OperationAction.UPDATE, remark = "销售出库-部分发货")
    public ApiResponse<SalesOut> partialShip(@PathVariable Long id,
                                              @RequestBody SalesOutPartialShipRequest request) {
        return ApiResponse.ok(service.partialShip(id, request.getLines()));
    }

    /**
     * v3.7.3 部分取消（按明细行，恢复库存）
     */
    @PostMapping("/{id}/cancel-partial")
    @OperationLog(businessType = "salesOut", action = OperationAction.UPDATE, remark = "销售出库-部分取消")
    public ApiResponse<SalesOut> cancelPartial(@PathVariable Long id,
                                                @RequestBody SalesOutCancelPartialRequest request) {
        return ApiResponse.ok(service.cancelPartial(id, request.getLines(), request.getReason()));
    }

    /**
     * v3.7.3 整单作废（已发货的全部恢复库存）
     */
    @PostMapping("/{id}/cancel-full")
    @OperationLog(businessType = "salesOut", action = OperationAction.UPDATE, remark = "销售出库-整单作废")
    public ApiResponse<SalesOut> cancelFull(@PathVariable Long id,
                                             @RequestBody(required = false) SalesOutCancelFullRequest request) {
        String reason = request == null ? null : request.getReason();
        return ApiResponse.ok(service.cancelFull(id, reason));
    }
}
