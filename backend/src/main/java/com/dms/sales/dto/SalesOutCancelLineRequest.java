/*
 * 销售出库-部分取消请求 DTO（按明细行）。
 */
package com.dms.sales.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesOutCancelLineRequest {
    /** sales_out_lines.id */
    private Long lineId;
    /** 取消数量（<= 该行累计已发 - 累计已取消） */
    private BigDecimal cancelQty;
}