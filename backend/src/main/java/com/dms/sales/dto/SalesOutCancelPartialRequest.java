/*
 * 销售出库-部分取消请求 DTO。
 */
package com.dms.sales.dto;

import lombok.Data;

import java.util.List;

@Data
public class SalesOutCancelPartialRequest {
    private List<SalesOutCancelLineRequest> lines;
    private String reason;
}