/*
 * 首页工作台控制器：/api/home/dashboard
 */
package com.dms.home.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.dms.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService service;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(service.getDashboard(TenantContext.getTenantId(), TenantContext.getUserId()));
    }
}
