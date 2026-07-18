/*
 * 销售发票上传请求 DTO。
 */
package com.dms.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesInvoiceUploadRequest {
    private Long refSalesOutId;
    private String invoiceNo;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private String imageUrl;
}
