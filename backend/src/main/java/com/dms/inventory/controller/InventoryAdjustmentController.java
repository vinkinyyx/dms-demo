/*
 * 库存调整控制器：/api/inventory-adjustments
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.inventory.dto.AdjustmentCreateRequest;
import com.dms.inventory.entity.InventoryAdjustment;
import com.dms.inventory.service.AdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final AdjustmentService service;

    @PostMapping
    public ApiResponse<InventoryAdjustment> submit(@RequestBody AdjustmentCreateRequest request) {
        return ApiResponse.ok(service.submit(request.getAdjustment(), request.getLines()));
    }
}
