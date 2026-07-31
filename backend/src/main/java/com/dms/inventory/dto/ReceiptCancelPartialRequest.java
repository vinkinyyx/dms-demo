/*
 * 收货单部分取消请求 DTO。
 */
package com.dms.inventory.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReceiptCancelPartialRequest {
    private List<ReceiptCancelLineRequest> lines;
    private String reason;
}