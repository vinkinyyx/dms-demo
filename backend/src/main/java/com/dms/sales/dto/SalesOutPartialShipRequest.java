/*
 * 销售出库-部分出库请求 DTO。
 */
package com.dms.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalesOutPartialShipRequest {
    private List<ShipLineRequest> lines;
    private String remark;

    @Data
    public static class ShipLineRequest {
        /** 对应 sales_out_lines 中 expected_qty>0 的应发行 ID */
        private Long expectedLineId;
        /** productId 必填 */
        private Long productId;
        /** warehouseId 必填 */
        private Long warehouseId;
        /** 本次发货数量 */
        private BigDecimal qty;
        /** 批次号（批次管理产品必填） */
        private String batchNo;
        /** 序列号（序列号管理产品必填） */
        private String serialNo;
        /** 报价单价（可选） */
        private BigDecimal unitPrice;
    }
}
