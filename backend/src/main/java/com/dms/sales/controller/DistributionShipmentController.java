package com.dms.sales.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.enums.OperationAction;
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
    @OperationLog(businessType = "distributionShipment", action = OperationAction.CREATE, remark = "分销出库-创建")
    public ApiResponse<DistributionShipment> create(@RequestBody(required = false) DistributionShipmentCreateRequest request) {
        if (request == null || request.getShipment() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "shipment must not be empty");
        }
        return ApiResponse.ok(service.create(request.getShipment(), request.getLines()));
    }
}