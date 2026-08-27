/*
 * 代金券核销/占用请求。
 */
package com.dms.voucher.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VoucherAcquireRequest {

    @NotNull(message = "voucherId 不能为空")
    private Long voucherId;

    @NotNull(message = "orderId 不能为空")
    private Long orderId;

    private String orderCode;

    @NotNull(message = "usedAmount 不能为空")
    private BigDecimal usedAmount;

    /** 订单原价合计，用于二次校验 min_spend/面值 */
    private BigDecimal orderOriginalAmount;
}
