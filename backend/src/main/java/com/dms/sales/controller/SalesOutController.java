/*
 * 销售出库控制器：/api/sales-outs
 */
package com.dms.sales.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.sales.dto.SalesOutCreateRequest;
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

    @GetMapping
    public ApiResponse<PageResult<SalesOut>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @PostMapping
    public ApiResponse<SalesOut> create(@RequestBody SalesOutCreateRequest request) {
        return ApiResponse.ok(service.create(request.getSalesOut(), request.getLines()));
    }

    @PostMapping("/{id}/red-cancel")
    public ApiResponse<SalesOut> redCancel(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(service.redCancel(id, reason));
    }
}
