/*
 * 促销预览 REST 控制器：/api/promotions/preview 接收草稿订单 → 调用引擎返回结果。
 */
package com.dms.promotion.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.dms.promotion.dto.PromotionEvaluationResult;
import com.dms.promotion.dto.PromotionPreviewRequest;
import com.dms.promotion.service.PromotionEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
public class PromotionPreviewController {

    private final PromotionEngine engine;

    @PostMapping("/api/promotions/preview")
    public ApiResponse<PromotionEvaluationResult> preview(@RequestBody PromotionPreviewRequest request) {
        PromotionEvaluationResult r = engine.evaluate(
                TenantContext.getTenantId(),
                request.getDealerId(),
                request.getLines());
        return ApiResponse.ok(r);
    }
}
