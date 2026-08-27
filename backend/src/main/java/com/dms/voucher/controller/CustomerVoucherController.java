/*
 * 客户代金券接口：厂家发放/管理、可用券查询、下单核销/返还（供 order 模块内部调用）。
 */
package com.dms.voucher.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.voucher.dto.VoucherAcquireRequest;
import com.dms.voucher.dto.VoucherBatchIssueRequest;
import com.dms.voucher.dto.VoucherDTO;
import com.dms.voucher.entity.CustomerVoucherUsage;
import com.dms.voucher.service.CustomerVoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customer-vouchers")
@RequiredArgsConstructor
public class CustomerVoucherController {

    private final CustomerVoucherService voucherService;

    /** 厂家批量发放。 */
    @PostMapping("/batch-issue")
    @PreAuthorize("@perm.hasAny('customer_voucher:manage','customer_voucher:create','promotion:manage')")
    public ApiResponse<List<VoucherDTO>> batchIssue(@Valid @RequestBody VoucherBatchIssueRequest request) {
        return ApiResponse.ok(voucherService.batchIssue(request));
    }

    /** 可用券查询：下单页使用。 */
    @GetMapping("/available")
    public ApiResponse<List<VoucherDTO>> available(@RequestParam Long dealerId,
                                                    @RequestParam(required = false) BigDecimal amount,
                                                    @RequestParam(required = false) String productIds) {
        List<Long> pidList = parseIds(productIds);
        return ApiResponse.ok(voucherService.available(dealerId, amount, pidList));
    }

    /** 分页列表（管理端）。 */
    @GetMapping
    @PreAuthorize("@perm.hasAny('customer_voucher:view','customer_voucher:search','promotion:view')")
    public ApiResponse<PageResult<VoucherDTO>> list(PageQuery pageQuery,
                                                     @RequestParam(required = false) Long dealerId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(voucherService.list(pageQuery, dealerId, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('customer_voucher:view','customer_voucher:search','promotion:view')")
    public ApiResponse<VoucherDTO> detail(@PathVariable Long id) {
        return ApiResponse.ok(voucherService.detail(id));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@perm.hasAny('customer_voucher:manage','promotion:manage')")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        voucherService.disable(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("@perm.hasAny('customer_voucher:manage','promotion:manage')")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        voucherService.enable(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@perm.hasAny('customer_voucher:manage','promotion:manage')")
    public ApiResponse<Void> voidVoucher(@PathVariable Long id) {
        voucherService.voidVoucher(id);
        return ApiResponse.ok();
    }

    /** 下单核销（order 模块在提交/确认时调用）。一单一张由调用方保证。 */
    @PostMapping("/acquire")
    public ApiResponse<Long> acquire(@Valid @RequestBody VoucherAcquireRequest request) {
        CustomerVoucherUsage usage = voucherService.acquire(request);
        return ApiResponse.ok(usage.getId());
    }

    /** 整单未出库作废返还（order 模块调用）。 */
    @PostMapping("/release")
    public ApiResponse<Void> release(@RequestParam Long orderId) {
        voucherService.release(orderId);
        return ApiResponse.ok();
    }

    private List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }
}
