package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-switches")
@RequiredArgsConstructor
@PreAuthorize("@perm.isTenantAdmin()")
public class SystemSwitchController {

    private final SystemSettingService systemSettingService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(aggregateSwitches());
    }

    @PostMapping
    public ApiResponse<List<Map<String, Object>>> update(@RequestBody Map<String, Object> body) {
        String key = body.get("key") == null ? null : String.valueOf(body.get("key"));
        Object enabled = body.get("enabled");
        if (key == null || key.isBlank() || enabled == null) {
            return ApiResponse.fail(40001, "参数 key 和 enabled 不能为空");
        }
        boolean value = Boolean.parseBoolean(String.valueOf(enabled));
        if (SystemSettingService.KEY_ORDER_AUTHZ_ENFORCE.equals(key)) {
            systemSettingService.setOrderAuthzEnforced(value);
        } else if (SystemSettingService.KEY_MAIL_MASTER.equals(key)
                || SystemSettingService.KEY_MAIL_REPORT.equals(key)
                || SystemSettingService.KEY_MAIL_APPROVAL.equals(key)) {
            systemSettingService.updateMailSwitch(key, value);
        } else {
            return ApiResponse.fail(40001, "不支持的配置项");
        }
        return ApiResponse.ok(aggregateSwitches());
    }

    private List<Map<String, Object>> aggregateSwitches() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> authz = new LinkedHashMap<>();
        authz.put("key", SystemSettingService.KEY_ORDER_AUTHZ_ENFORCE);
        authz.put("label", "授权与下单挂钩");
        authz.put("description", "开启后，无有效授权的经销商不能下单/销售出库；关闭（解耦）时可直接下单");
        boolean enforced = systemSettingService.isOrderAuthzEnforced();
        authz.put("enabled", enforced);
        authz.put("value", enforced);
        authz.put("configured", true);
        authz.put("defaultValue", false);
        authz.put("scope", "tenant");
        list.add(authz);

        for (Map<String, Object> mail : systemSettingService.getMailSwitches()) {
            mail.put("scope", "global");
            list.add(mail);
        }
        return list;
    }
}
