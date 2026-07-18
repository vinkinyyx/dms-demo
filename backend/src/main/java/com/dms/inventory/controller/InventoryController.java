/*
 * 库存查询控制器：/api/inventory
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.inventory.entity.Inventory;
import com.dms.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Validated
public class InventoryController {

    private final InventoryService service;

    @GetMapping
    public ApiResponse<PageResult<Inventory>> query(@RequestParam(required = false) Long dealerId,
                                                     @RequestParam(required = false) Long productId,
                                                     @RequestParam(required = false) String batchNo,
                                                     @Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.query(dealerId, productId, batchNo, pageQuery));
    }
}
