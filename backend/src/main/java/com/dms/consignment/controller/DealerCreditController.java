package com.dms.consignment.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageResult;
import com.dms.common.PageQuery;
import jakarta.validation.Valid;
import com.dms.consignment.service.DealerCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "经销商资信与账期")
@RestController
@RequestMapping("/api/dealer-credit")
@RequiredArgsConstructor
public class DealerCreditController {

    private final DealerCreditService dealerCreditService;

    @Operation(summary = "资信与账期列表（含信用额度/占用、账期、寄售额度/占用）")
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> page(@Valid PageQuery pageQuery,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(dealerCreditService.page(pageQuery.getPage(), pageQuery.getSize(), keyword));
    }
}
