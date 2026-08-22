package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.masterdata.entity.ProductBundle;
import com.dms.masterdata.entity.ProductBundleLine;
import com.dms.masterdata.service.ProductBundleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product-bundles")
@RequiredArgsConstructor
@Validated
public class ProductBundleController {

    private final ProductBundleService service;

    @GetMapping
    public ApiResponse<PageResult<ProductBundle>> list(@Valid PageQuery pageQuery,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String code,
                                                       @RequestParam(required = false) String name,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String versionStatus,
                                                       @RequestParam(required = false) Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, keyword, code, name, status, versionStatus));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductBundle> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/product/{productId}/active")
    public ApiResponse<ProductBundle> activeByProduct(@PathVariable Long productId) {
        return ApiResponse.ok(service.activeByProduct(productId));
    }

    @PostMapping("/{id}/new-version")
    @OperationLog(businessType = "productBundle", action = OperationAction.CREATE, remark = "BOM-新建版本")
    public ApiResponse<ProductBundle> newVersion(@PathVariable Long id) {
        return ApiResponse.ok(service.createNewVersion(id));
    }

    @PostMapping("/{id}/activate")
    @OperationLog(businessType = "productBundle", action = OperationAction.UPDATE, remark = "BOM-发布草稿版本")
    public ApiResponse<ProductBundle> activate(@PathVariable Long id) {
        return ApiResponse.ok(service.activateDraft(id));
    }
    @GetMapping("/by-product/{productId}")
    public ApiResponse<List<ProductBundle>> listByProduct(@PathVariable Long productId) {
        return ApiResponse.ok(service.listByProduct(productId));
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<ProductBundleLine>> listLines(@PathVariable Long id) {
        return ApiResponse.ok(service.listLines(id));
    }

    @GetMapping("/{id}/lines/fixed")
    public ApiResponse<List<ProductBundleLine>> listFixedLines(@PathVariable Long id) {
        return ApiResponse.ok(service.listFixedLines(id));
    }

    @PostMapping
    @OperationLog(businessType = "productBundle", action = OperationAction.CREATE, remark = "组套-创建")
    public ApiResponse<ProductBundle> create(@RequestBody ProductBundle request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "productBundle", action = OperationAction.UPDATE, remark = "组套-更新")
    public ApiResponse<ProductBundle> update(@PathVariable Long id, @RequestBody ProductBundle request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @OperationLog(businessType = "productBundle", action = OperationAction.UPDATE, remark = "组套-停用")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/lines")
    @OperationLog(businessType = "productBundle", action = OperationAction.CREATE, remark = "组套明细-添加")
    public ApiResponse<ProductBundleLine> addLine(@PathVariable Long id, @RequestBody ProductBundleLine request) {
        return ApiResponse.ok(service.addLine(id, request));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @OperationLog(businessType = "productBundle", action = OperationAction.DELETE, remark = "组套明细-删除")
    public ApiResponse<Void> removeLine(@PathVariable Long id, @PathVariable Long lineId) {
        service.removeLine(id, lineId);
        return ApiResponse.ok();
    }
}
