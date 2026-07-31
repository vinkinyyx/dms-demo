/*
 * 销售出库-整单取消请求 DTO。
 */
package com.dms.sales.dto;

import lombok.Data;

@Data
public class SalesOutCancelFullRequest {
    private String reason;
}