package com.dms.openapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ERP 销售出库回传单行（业务编码报文）。
 */
@Data
public class ErpOutboundLine {

    /** DMS 订单行内部 ID（与 productCode/productId 二选一，优先使用）。 */
    private Long sourceOrderLineId;

    /** DMS 产品编码（与 productId 二选一）。 */
    private String productCode;

    /** DMS 产品内部 ID。 */
    private Long productId;

    /** 本次出库数量，必须为正数。 */
    @NotNull(message = "qty 不能为空")
    @DecimalMin(value = "0.0001", message = "qty 必须大于 0")
    private BigDecimal qty;

    /** 批号。 */
    private String batchNo;

    /** 序列号（序列号管理产品建议填写）。 */
    private String serialNo;

    /** 出库单价（不含税），可选；不传则取订单行价格。 */
    private BigDecimal unitPrice;

    /** 行备注。 */
    private String remark;
}