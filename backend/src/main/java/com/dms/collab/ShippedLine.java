package com.dms.collab;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 厂家销售出库本次发货的一行（用于跨租户回传经销商收货入库）。
 * 价格不带入经销商环境；仅产品/数量/批次/序列号。
 */
@Data
public class ShippedLine {
    private Long productId;          // 厂家产品 id
    private String productCode;      // 厂家产品编码（错误提示用）
    private BigDecimal qty;
    private String batchNo;
    private String serialNo;
    private Long salesOrderLineId;   // 厂家销售订单行 id（溯源，可空）
    private Long outLineId;          // 厂家出库单本次发货执行行 id（分批幂等去重用，可空）

    // 对码后回填
    private Long dealerProductId;    // 经销商产品 id
    private String dealerProductCode;
}
