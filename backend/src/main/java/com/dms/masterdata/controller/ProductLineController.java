package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.masterdata.entity.ProductLine;
import com.dms.masterdata.service.ProductLineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product-lines")
@RequiredArgsConstructor
@Validated
public class ProductLineController {

    private final ProductLineService service;

    @GetMapping
    public ApiResponse<PageResult<ProductLine>> list(@Valid PageQuery pageQuery,
                                                     @RequestParam(required = false) Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, allParams));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductLine> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/by-level/{level}")
    public ApiResponse<List<ProductLine>> listByLevel(@PathVariable Integer level) {
        return ApiResponse.ok(service.listByLevel(level));
    }

    @GetMapping("/by-parent/{parentId}")
    public ApiResponse<List<ProductLine>> listChildren(@PathVariable Long parentId) {
        return ApiResponse.ok(service.listChildren(parentId));
    }

    @PostMapping
    @OperationLog(businessType = "productLine", action = OperationAction.CREATE, remark = "产品线-创建")
    public ApiResponse<ProductLine> create(@RequestBody(required = false) ProductLine request) {
        validateCreate(request);
        return ApiResponse.ok(service.create(request));
    }

    private void validateCreate(ProductLine request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "request body must not be empty");
        }
        for (String fieldName : java.util.List.of("code", "name", "level")) {
            try {
                java.lang.reflect.Field field = ProductLine.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(request);
                if (value == null || (value instanceof String text && text.isBlank())) {
                    throw new BusinessException(ErrorCode.PARAM_MISSING, fieldName + " must not be empty");
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "productLine", action = OperationAction.UPDATE, remark = "产品线-更新")
    public ApiResponse<ProductLine> update(@PathVariable Long id, @RequestBody ProductLine request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @OperationLog(businessType = "productLine", action = OperationAction.UPDATE, remark = "产品线-停用")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }
}
