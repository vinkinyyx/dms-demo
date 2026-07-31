/*
 * 收货单部分取消请求 DTO：按明细行取消。
 */
package com.dms.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReceiptConfirmFullRequest {
    private String remark;
}