/*
 * 盘点控制器：/api/stocktakes
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.inventory.dto.StocktakeUploadRequest;
import com.dms.inventory.entity.Stocktake;
import com.dms.inventory.service.StocktakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocktakes")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService service;

    @PostMapping
    public ApiResponse<Stocktake> upload(@RequestBody StocktakeUploadRequest request) {
        return ApiResponse.ok(service.upload(request.getStocktake(), request.getLines()));
    }
}
