/*
 * 促销 REST 控制器 /api/promotions。
 */
package com.dms.promotion.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.promotion.entity.Promotion;
import com.dms.promotion.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("@perm.hasAny('promotion:view','promotion:search') and !@perm.isDealer()")
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@Validated
public class PromotionController {

    private final PromotionService service;

    @GetMapping
    public ApiResponse<PageResult<Promotion>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<Promotion> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<Promotion> create(@RequestBody Promotion request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.hasAny('promotion:edit')")
    public ApiResponse<Promotion> update(@PathVariable Long id, @RequestBody Promotion request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ApiResponse.ok();
    }
}
