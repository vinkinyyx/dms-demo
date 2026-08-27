/*
 * 代金券可用查询请求参数。
 */
package com.dms.voucher.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class VoucherAvailableQuery {

    private Long dealerId;

    /** 订单原价合计，用于 min_spend 门槛判断 */
    private BigDecimal amount;

    /** 订单内产品 id，用于范围(PRODUCT/CATEGORY)命中判断 */
    private List<Long> productIds;
}
