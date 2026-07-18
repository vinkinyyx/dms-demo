/*
 * 移库控制器：/api/stock-moves
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.inventory.dto.StockMoveCreateRequest;
import com.dms.inventory.entity.StockMove;
import com.dms.inventory.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock-moves")
@RequiredArgsConstructor
public class StockMoveController {

    private final StockMoveService service;

    @PostMapping
    public ApiResponse<StockMove> create(@RequestBody StockMoveCreateRequest request) {
        return ApiResponse.ok(service.create(request.getMove(), request.getLines()));
    }
}
