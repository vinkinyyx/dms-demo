package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.masterdata.entity.ProductPackageLevel;
import com.dms.masterdata.service.ProductPackageLevelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-package-levels")
@RequiredArgsConstructor
@Validated
public class ProductPackageLevelController {

    private final ProductPackageLevelService service;

    @GetMapping
    public ApiResponse<PageResult<ProductPackageLevel>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductPackageLevel> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/by-product/{productId}")
    public ApiResponse<List<ProductPackageLevel>> listByProduct(@PathVariable Long productId) {
        return ApiResponse.ok(service.listByProduct(productId));
    }

    @GetMapping("/by-product/{productId}/roots")
    public ApiResponse<List<ProductPackageLevel>> listRoots(@PathVariable Long productId) {
        return ApiResponse.ok(service.listRootsByProduct(productId));
    }

    @GetMapping("/by-parent/{parentId}")
    public ApiResponse<List<ProductPackageLevel>> listChildren(@PathVariable Long parentId) {
        return ApiResponse.ok(service.listChildren(parentId));
    }

    @PostMapping
    @OperationLog(businessType = "productPackageLevel", action = OperationAction.CREATE, remark = "包装层级-创建")
    public ApiResponse<ProductPackageLevel> create(@RequestBody ProductPackageLevel request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "productPackageLevel", action = OperationAction.UPDATE, remark = "包装层级-更新")
    public ApiResponse<ProductPackageLevel> update(@PathVariable Long id, @RequestBody ProductPackageLevel request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @OperationLog(businessType = "productPackageLevel", action = OperationAction.UPDATE, remark = "包装层级-停用")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }
}
