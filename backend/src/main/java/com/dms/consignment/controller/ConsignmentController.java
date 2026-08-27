package com.dms.consignment.controller;

import com.dms.common.ApiResponse;
import com.dms.consignment.service.ConsignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "寄售库存")
@RestController
@RequestMapping("/api/consignment")
@RequiredArgsConstructor
public class ConsignmentController {

    private final ConsignmentService consignmentService;

    @Operation(summary = "开票可选寄售库存（按经销商，返回可用量>0的台账行）")
    @GetMapping("/available")
    public ApiResponse<List<Map<String, Object>>> available(@RequestParam(required = false) Long dealerId,
                                                            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(consignmentService.availableForInvoice(dealerId, keyword));
    }
}
