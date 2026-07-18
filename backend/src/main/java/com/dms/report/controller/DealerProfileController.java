/*
 * 经销商画像控制器：/api/dealer-profile
 */
package com.dms.report.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.report.service.DealerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dealer-profile")
@RequiredArgsConstructor
public class DealerProfileController {

    private final DealerProfileService service;

    @GetMapping("/{dealerId}")
    public ApiResponse<Object> basic(@PathVariable Long dealerId) {
        return ApiResponse.ok(service.getBasic(dealerId));
    }

    @GetMapping("/{dealerId}/{tab}")
    public ApiResponse<Object> tab(@PathVariable Long dealerId, @PathVariable String tab) {
        return switch (tab) {
            case "basic" -> ApiResponse.ok(service.getBasic(dealerId));
            case "kpi" -> ApiResponse.ok(service.getKpi(dealerId));
            case "achievement" -> ApiResponse.ok(service.getAchievement(dealerId));
            case "rebate" -> ApiResponse.ok(service.getRebate(dealerId));
            case "contracts" -> ApiResponse.ok(service.getContracts(dealerId));
            case "inventory" -> ApiResponse.ok(service.getInventory(dealerId));
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "未知 tab: " + tab);
        };
    }
}
