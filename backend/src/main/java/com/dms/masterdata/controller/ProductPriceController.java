package com.dms.masterdata.controller;

import com.dms.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import com.dms.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import com.dms.masterdata.service.ProductPriceService;

@RequestMapping("/api/product-prices")
@RestController
@RequiredArgsConstructor
@Validated
public class ProductPriceController {

    private final ProductPriceService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> list( @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long id, @RequestParam(required = false) String productId, @RequestParam(required = false) String priceScope, @RequestParam(required = false) String priceType, @RequestParam(required = false) String partnerType, @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String partnerName, @RequestParam(required = false) String priceContext, @RequestParam(required = false) Long bomParentProductId, @RequestParam(required = false) Boolean includeComponents, @RequestParam(required = false) String status, @RequestParam(required = false) String keyword, @RequestParam(required = false) String productCode, @RequestParam(required = false) String validFrom, @RequestParam(required = false) String validTo) {
        return service.list(page, size, id, productId, priceScope, priceType, partnerType, partnerId, partnerName, priceContext, bomParentProductId, includeComponents, status, keyword, productCode, validFrom, validTo);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<Map<String, Object>> activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Map<String, Object>> deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return service.delete(id);
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        return service.export();
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        return service.exportTemplate();
    }

    @PostMapping("/batch-import")
    public ApiResponse<Map<String, Object>> batchImportUnsupported() {
        return service.batchImportUnsupported();
    }

    @PostMapping("/batch-import-legacy")
    public ApiResponse<Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        return service.batchImport(file);
    }

}
