/*
 * 厂家前台只读接口：查询归属当前厂家的经销商租户列表，用于产品对码。
 */
package com.dms.platform.mapping.controller;

import com.dms.common.ApiResponse;
import com.dms.platform.mapping.dto.DealerTenantSimpleDTO;
import com.dms.platform.mapping.service.ProductMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/my-dealer-tenants")
@RequiredArgsConstructor
public class MyDealerTenantController {

    private final ProductMappingService mappingService;

    @GetMapping
    public ApiResponse<List<DealerTenantSimpleDTO>> myDealerTenants() {
        return ApiResponse.ok(mappingService.myDealerTenants());
    }
}