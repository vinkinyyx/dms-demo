/*
 * 采购发票上传请求 DTO。
 */
package com.dms.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseInvoiceUploadRequest {
    private Long refOrderId;
    private String invoiceNo;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    private String imageUrl;
}
