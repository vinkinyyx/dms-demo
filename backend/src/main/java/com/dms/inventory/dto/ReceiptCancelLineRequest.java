/*
 * 收货单部分取消请求 DTO：按明细行取消。
 */
package com.dms.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReceiptCancelLineRequest {
    /** 收货明细行 ID（receipt_lines.id），部分取消时必填 */
    private Long lineId;
    /** 取消数量（<= 累计已收 - 累计已取消） */
    private BigDecimal cancelQty;
}