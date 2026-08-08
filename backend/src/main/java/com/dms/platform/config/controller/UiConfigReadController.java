/*
 * 涓氬姟鍓嶅彴 UI 閰嶇疆璇诲彇鎺ュ彛锛氭寜褰撳墠绉熸埛绫诲瀷杩斿洖鍚敤閰嶇疆銆? */
package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.platform.config.dto.FilterConfigDTO;
import com.dms.platform.config.dto.PageConfigDTO;
import com.dms.platform.config.service.UiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UiConfigReadController {

    private final UiConfigService uiConfigService;

    @GetMapping("/ui-configs/pages/{pageKey}")
    public ApiResponse<List<PageConfigDTO>> pageConfig(@PathVariable String pageKey) {
        return ApiResponse.ok(uiConfigService.pageForTenant(pageKey, requireTenantType()));
    }

    @GetMapping("/filter-configs/pages/{pageKey}")
    public ApiResponse<List<FilterConfigDTO>> filterConfig(@PathVariable String pageKey) {
        return ApiResponse.ok(uiConfigService.filtersForTenant(pageKey, requireTenantType(), TenantContext.getTenantId()));
    }

    private String requireTenantType() {
        String tenantType = TenantContext.getTenantType();
        if (tenantType == null || tenantType.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "鏃犳硶璇嗗埆绉熸埛绫诲瀷");
        }
        return tenantType;
    }
}