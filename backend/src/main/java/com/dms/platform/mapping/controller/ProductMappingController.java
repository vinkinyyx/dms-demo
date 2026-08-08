/*
 * 产品对码接口（厂家租户前台）。
 */
package com.dms.platform.mapping.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.platform.mapping.dto.MappingImportPreviewResponse;
import com.dms.platform.mapping.dto.ProductMappingCreateRequest;
import com.dms.platform.mapping.dto.ProductMappingDTO;
import com.dms.platform.mapping.dto.ProductMappingUpdateRequest;
import com.dms.platform.mapping.service.ProductMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/product-mappings")
@RequiredArgsConstructor
public class ProductMappingController {

    private final ProductMappingService mappingService;

    @GetMapping
    public ApiResponse<PageResult<ProductMappingDTO>> list(@Valid PageQuery pageQuery,
                                                           @RequestParam(required = false) UUID dealerTenantId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status) {
        return ApiResponse.ok(mappingService.list(pageQuery, dealerTenantId, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductMappingDTO> get(@PathVariable Long id) {
        return ApiResponse.ok(mappingService.get(id));
    }

    @PostMapping
    public ApiResponse<ProductMappingDTO> create(@Valid @RequestBody ProductMappingCreateRequest request) {
        return ApiResponse.ok(mappingService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductMappingDTO> update(@PathVariable Long id,
                                                 @RequestBody ProductMappingUpdateRequest request) {
        return ApiResponse.ok(mappingService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        mappingService.setStatus(id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        mappingService.setStatus(id, false);
        return ApiResponse.ok();
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] data = mappingService.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        com.dms.common.util.ContentDispositionUtils.attachment("product-mapping-template.xlsx"))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/import/preview")
    public ApiResponse<MappingImportPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(mappingService.importPreview(file));
    }

    @PostMapping("/import/confirm")
    public ApiResponse<MappingImportPreviewResponse> confirm(@RequestParam("batchId") Long batchId) {
        return ApiResponse.ok(mappingService.confirmImport(batchId));
    }

    @GetMapping("/import-batches/{id}/errors")
    public ResponseEntity<byte[]> errors(@PathVariable Long id) {
        byte[] data = mappingService.errorReport(id);
        String filename = URLEncoder.encode("mapping-errors-" + id + ".xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}