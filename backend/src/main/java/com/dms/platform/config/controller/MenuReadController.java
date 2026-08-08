/*
 * 业务前台菜单读取接口：按当前租户类型返回启用菜单。
 */
package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.platform.config.dto.PlatformMenuDTO;
import com.dms.platform.config.service.PlatformMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuReadController {

    private final PlatformMenuService menuService;

    @GetMapping
    public ApiResponse<List<PlatformMenuDTO>> myMenus() {
        String tenantType = TenantContext.getTenantType();
        if (tenantType == null || tenantType.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无法识别租户类型");
        }
        return ApiResponse.ok(menuService.listForTenant(tenantType));
    }
}