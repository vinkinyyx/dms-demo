/*
 * 收货单整单取消请求 DTO。
 */
package com.dms.inventory.dto;

import lombok.Data;

@Data
public class ReceiptCancelFullRequest {
    private String reason;
}