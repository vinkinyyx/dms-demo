/*
 * 平台后台-定时邮件开关管理接口（运行时可切换，无需重启）。
 * 路径 /api/admin/** 仅接受平台后台 token。
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/mail-config")
@RequiredArgsConstructor
public class AdminMailConfigController {

    private final SystemSettingService systemSettingService;

    @GetMapping("/switches")
    public ApiResponse<List<Map<String, Object>>> switches() {
        return ApiResponse.ok(systemSettingService.getMailSwitches());
    }

    @PostMapping("/switches")
    public ApiResponse<List<Map<String, Object>>> updateSwitch(@RequestBody Map<String, Object> body) {
        String key = body.get("key") == null ? null : String.valueOf(body.get("key"));
        Object enabled = body.get("enabled");
        if (key == null || key.isBlank() || enabled == null) {
            return ApiResponse.fail(40001, "参数 key 和 enabled 不能为空");
        }
        boolean value = Boolean.parseBoolean(String.valueOf(enabled));
        try {
            return ApiResponse.ok(systemSettingService.updateMailSwitch(key, value));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }
}
