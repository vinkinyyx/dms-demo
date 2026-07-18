/*
 * 销售发票控制器：/api/sales-invoices
 */
package com.dms.invoice.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.invoice.dto.SalesInvoiceUploadRequest;
import com.dms.invoice.entity.SalesInvoice;
import com.dms.invoice.service.SalesInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-invoices")
@RequiredArgsConstructor
@Validated
public class SalesInvoiceController {

    private final SalesInvoiceService service;

    @GetMapping
    public ApiResponse<PageResult<SalesInvoice>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @PostMapping
    public ApiResponse<SalesInvoice> upload(@RequestBody SalesInvoiceUploadRequest req) {
        return ApiResponse.ok(service.upload(req.getRefSalesOutId(), req.getInvoiceNo(),
                req.getAmount(), req.getTaxAmount(), req.getImageUrl()));
    }
}
