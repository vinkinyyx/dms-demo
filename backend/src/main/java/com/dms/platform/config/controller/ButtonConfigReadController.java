/*
 * 业务前台按钮配置读取接口。
 * 一次返回 toolbar + row 两套合并后的按钮列表（已合并平台默认 + 当前租户覆盖）。
 */
package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.platform.config.dto.ButtonConfigDTO;
import com.dms.platform.config.service.PlatformButtonConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/button-configs")
@RequiredArgsConstructor
public class ButtonConfigReadController {

    private final PlatformButtonConfigService service;

    @GetMapping("/pages/{pageKey}/{scope}")
    public ApiResponse<List<ButtonConfigDTO>> merged(@PathVariable String pageKey,
                                                     @PathVariable String scope) {
        return ApiResponse.ok(service.mergedForTenant(requireTenantId(), pageKey, scope));
    }

    private java.util.UUID requireTenantId() {
        java.util.UUID id = TenantContext.getTenantId();
        if (id == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无法识别当前租户");
        }
        return id;
    }
}