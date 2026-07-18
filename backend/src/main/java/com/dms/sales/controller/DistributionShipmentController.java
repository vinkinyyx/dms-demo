/*
 * 分销出库控制器：/api/distribution-shipments
 */
package com.dms.sales.controller;

import com.dms.common.ApiResponse;
import com.dms.sales.dto.DistributionShipmentCreateRequest;
import com.dms.sales.entity.DistributionShipment;
import com.dms.sales.service.DistributionShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/distribution-shipments")
@RequiredArgsConstructor
public class DistributionShipmentController {

    private final DistributionShipmentService service;

    @PostMapping
    public ApiResponse<DistributionShipment> create(@RequestBody DistributionShipmentCreateRequest request) {
        return ApiResponse.ok(service.create(request.getShipment(), request.getLines()));
    }
}
