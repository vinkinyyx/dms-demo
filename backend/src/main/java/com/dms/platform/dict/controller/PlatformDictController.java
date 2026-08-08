/*
 * 平台后台全局字典管理（tenant_id IS NULL）。业务前台只读接口见 /api/dicts/**。
 */
package com.dms.platform.dict.controller;

import com.dms.common.ApiResponse;
import com.dms.platform.dict.dto.DictItemSaveRequest;
import com.dms.platform.dict.dto.DictItemUpdateRequest;
import com.dms.platform.dict.dto.DictTypeSaveRequest;
import com.dms.platform.dict.dto.DictTypeUpdateRequest;
import com.dms.platform.dict.service.PlatformDictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dicts")
@RequiredArgsConstructor
public class PlatformDictController {

    private final PlatformDictService dictService;

    @GetMapping("/types")
    public ApiResponse<List<Map<String, Object>>> listTypes() {
        return ApiResponse.ok(dictService.listTypes());
    }

    @PostMapping("/types")
    public ApiResponse<Map<String, Object>> createType(@Valid @RequestBody DictTypeSaveRequest request) {
        return ApiResponse.ok(dictService.createType(
                request.getCode(), request.getName(), request.getDescription()));
    }

    @PutMapping("/types/{id}")
    public ApiResponse<Void> updateType(@PathVariable Long id,
                                        @Valid @RequestBody DictTypeUpdateRequest request) {
        dictService.updateType(id, request.getName(), request.getDescription());
        return ApiResponse.ok();
    }

    @GetMapping("/types/{code}/items")
    public ApiResponse<List<Map<String, Object>>> listItems(@PathVariable String code) {
        return ApiResponse.ok(dictService.listItems(code));
    }

    @PostMapping("/types/{code}/items")
    public ApiResponse<Map<String, Object>> createItem(@PathVariable String code,
                                                       @Valid @RequestBody DictItemSaveRequest request) {
        return ApiResponse.ok(dictService.createItem(
                code, request.getCode(), request.getName(), request.getSeq()));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<Void> updateItem(@PathVariable Long id,
                                        @Valid @RequestBody DictItemUpdateRequest request) {
        dictService.updateItem(id, request.getCode(), request.getName(), request.getSeq());
        return ApiResponse.ok();
    }

    @PostMapping("/items/{id}/enable")
    public ApiResponse<Void> enableItem(@PathVariable Long id) {
        dictService.setItemStatus(id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/items/{id}/disable")
    public ApiResponse<Void> disableItem(@PathVariable Long id) {
        dictService.setItemStatus(id, false);
        return ApiResponse.ok();
    }

    @PostMapping("/refresh-cache")
    public ApiResponse<Void> refreshCache() {
        dictService.refreshCache();
        return ApiResponse.ok();
    }
}