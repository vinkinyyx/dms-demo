/*
 * 盘点控制器：/api/stocktakes
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.inventory.dto.StocktakeUploadRequest;
import com.dms.inventory.entity.Stocktake;
import com.dms.inventory.service.StocktakeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocktakes")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService service;

    @GetMapping
    public ApiResponse<PageResult<Stocktake>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @PostMapping
    public ApiResponse<Stocktake> upload(@RequestBody(required = false) StocktakeUploadRequest request) {
        if (request == null || request.getStocktake() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "stocktake must not be empty");
        }
        return ApiResponse.ok(service.upload(request.getStocktake(), request.getLines()));
    }
}
