package com.dms.v4;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class V4CalcResult {
    private List<V4Line> lines;
    private List<String> promotionMessages;
    private String pricingMode;          // NORMAL / FIXED_PRICE / ZERO_ORDER / VOUCHER
    private BigDecimal originalAmount;   // 基础原价合计
    private BigDecimal productDiscountTotal;
    private BigDecimal promoDiscountTotal;
    private BigDecimal lineDiscountTotal;
    private BigDecimal dealerDiscountTotal;
    private BigDecimal headerDiscountTotal;
    private BigDecimal finalAmount;      // 整单含税合计（不含券抵扣）
    private BigDecimal voucherAmount;    // 券抵扣金额
    private BigDecimal payableAmount;    // finalAmount - voucherAmount
    private BigDecimal amountExclTax;
    private BigDecimal taxAmount;
    private Long voucherId;
    private String voucherCode;
    private BigDecimal fixedPrice;
    private String headerDiscountDirection;  // REDUCE(减)/ADD(加，高开)
    private String headerDiscountType;
    private BigDecimal headerDiscountValue;

    public V4CalcResult(List<V4Line> lines, List<String> promotionMessages) {
        this.lines = lines;
        this.promotionMessages = promotionMessages == null ? new ArrayList<>() : promotionMessages;
        this.pricingMode = "NORMAL";
        this.originalAmount = BigDecimal.ZERO;
        this.productDiscountTotal = BigDecimal.ZERO;
        this.promoDiscountTotal = BigDecimal.ZERO;
        this.lineDiscountTotal = BigDecimal.ZERO;
        this.dealerDiscountTotal = BigDecimal.ZERO;
        this.headerDiscountTotal = BigDecimal.ZERO;
        this.finalAmount = BigDecimal.ZERO;
        this.voucherAmount = BigDecimal.ZERO;
        this.payableAmount = BigDecimal.ZERO;
        this.amountExclTax = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
    }

    public static V4CalcResult of(List<V4Line> lines, List<String> messages) {
        return new V4CalcResult(lines, messages == null ? new ArrayList<>() : messages);
    }
}
