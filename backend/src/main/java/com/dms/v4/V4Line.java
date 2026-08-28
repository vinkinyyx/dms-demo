package com.dms.v4;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class V4Line {
    private Long productId;
    private String productCode;
    private String productName;
    private String productSpec;
    private BigDecimal qty;
    private BigDecimal componentQty;
    private BigDecimal unitPriceExclTax;
    private BigDecimal taxRate;
    private BigDecimal standardPriceInclTax;
    private BigDecimal standardAmount;
    private String lineDiscountType;
    private BigDecimal lineDiscountValue;
    private String lineDiscountDirection; // REDUCE(减)/ADD(加，高开)
    private BigDecimal lineDiscountAmount;
    private BigDecimal promoDiscountAmount;
    private BigDecimal headerDiscountAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private BigDecimal amountExclTax;
    private BigDecimal taxAmount;
    private boolean gift;
    private boolean closed;
    private Long bomParentProductId;
    private String bomVersion;
    private String bomGroupNo;
    private String batchNo;
    private String serialNo;
    private Long consignmentStockId; // v4.4.1 开票订单拣选的寄售库存台账行 id
    private String lineLevel;
    private boolean groupHeader;
    private Long bomParentLineId;
    // v4.3.0 计价扩展
    private String priceSource;          // CONTRACT / DEALER / GLOBAL
    private BigDecimal basePriceInclTax; // 基础含税单价（合同价>客户价>全局价）
    private BigDecimal productDiscountRate;
    private BigDecimal productDiscountAmount;
    private BigDecimal dealerDiscountAmount; // 客户全局折扣分摊到行
    private String promoType;               // QTY_DISCOUNT / QTY_REDUCE / GIFT
    private Long promotionId;
    private Long promoHitId;
    private BigDecimal unitPriceInclTax;    // EA 成交含税单价（2 位，尾差吸收）
    private boolean lineZero;               // 手动 0 金额行
    private String lineLabel;               // 行号+SKU 错误定位
}

