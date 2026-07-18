/*
 * 采购发票控制器：/api/purchase-invoices
 */
package com.dms.invoice.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.invoice.dto.PurchaseInvoiceUploadRequest;
import com.dms.invoice.entity.PurchaseInvoice;
import com.dms.invoice.service.PurchaseInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-invoices")
@RequiredArgsConstructor
@Validated
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService service;

    @GetMapping
    public ApiResponse<PageResult<PurchaseInvoice>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @PostMapping
    public ApiResponse<PurchaseInvoice> upload(@RequestBody PurchaseInvoiceUploadRequest req) {
        return ApiResponse.ok(service.upload(req.getRefOrderId(), req.getInvoiceNo(),
                req.getAmount(), req.getTaxAmount(), req.getTaxRate(), req.getImageUrl()));
    }
}
