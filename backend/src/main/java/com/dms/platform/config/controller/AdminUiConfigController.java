/*
 * 平台后台页面字段与筛选项配置管理接口。
 */
package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.platform.config.dto.FilterConfigDTO;
import com.dms.platform.config.dto.FilterConfigUpsertRequest;
import com.dms.platform.config.dto.PageConfigDTO;
import com.dms.platform.config.dto.PageConfigUpsertRequest;
import com.dms.platform.config.service.UiConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUiConfigController {

    private final UiConfigService uiConfigService;

    @GetMapping("/page-configs")
    public ApiResponse<List<PageConfigDTO>> listPageConfigs(@RequestParam String pageKey,
                                                            @RequestParam String tenantType) {
        return ApiResponse.ok(uiConfigService.listPageConfigs(pageKey, tenantType));
    }

    @PutMapping("/page-configs")
    public ApiResponse<List<PageConfigDTO>> upsertPageConfigs(@Valid @RequestBody PageConfigUpsertRequest request) {
        return ApiResponse.ok(uiConfigService.upsertPageConfigs(
                request.getPageKey(), request.getTenantType(), request.getFields()));
    }

    @GetMapping("/filter-configs")
    public ApiResponse<List<FilterConfigDTO>> listFilterConfigs(@RequestParam String pageKey,
                                                                @RequestParam String tenantType) {
        return ApiResponse.ok(uiConfigService.listFilterConfigs(pageKey, tenantType));
    }

    @PutMapping("/filter-configs")
    public ApiResponse<List<FilterConfigDTO>> upsertFilterConfigs(@Valid @RequestBody FilterConfigUpsertRequest request) {
        return ApiResponse.ok(uiConfigService.upsertFilterConfigs(
                request.getPageKey(), request.getTenantType(), request.getFilters()));
    }

    @PostMapping("/page-configs/refresh-cache")
    public ApiResponse<Void> refreshCache() {
        uiConfigService.refreshCache();
        return ApiResponse.ok();
    }
}