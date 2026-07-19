/*
 * 商品分类 REST 控制器。
 */
package com.dms.masterdata.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.masterdata.entity.ProductCategory;
import com.dms.masterdata.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product-categories")
@RequiredArgsConstructor
@Validated
public class ProductCategoryController {

    private final ProductCategoryService service;

    @GetMapping
    public ApiResponse<PageResult<ProductCategory>> list(@Valid PageQuery pageQuery,
                                                         @RequestParam(required = false) java.util.Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, allParams));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductCategory> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<ProductCategory> create(@RequestBody ProductCategory request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductCategory> update(@PathVariable Long id, @RequestBody ProductCategory request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }
}
