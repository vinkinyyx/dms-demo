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
    private String lineLevel;
    private boolean groupHeader;
    private Long bomParentLineId;
}
