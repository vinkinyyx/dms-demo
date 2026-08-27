package com.dms.v4;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class V4Money {
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    public static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal qty(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal rate(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal div(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) return BigDecimal.ZERO;
        return a.divide(b, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal discount(BigDecimal base, String type, BigDecimal value) {
        base = money(base);
        if (type == null || value == null || value.signum() <= 0) return BigDecimal.ZERO;
        return money(signedDiscount(base, type, value).abs());
    }

    /**
     * 有符号折扣：返回“减”为负、“加(高开)”为正的金额。
     * 约定：value 为折扣本身（PERCENT 传百分比/小数，AMOUNT 传金额）；direction：null/REDUCE 减，ADD 加。
     * 若 value 本身为负数，直接视为“加”（高开）。
     */
    public static BigDecimal signedDiscount(BigDecimal base, String type, BigDecimal value, String direction) {
        if (value == null) return BigDecimal.ZERO;
        boolean add = "ADD".equalsIgnoreCase(direction) || value.signum() < 0;
        BigDecimal magnitude = value.abs();
        if (magnitude.signum() == 0) return BigDecimal.ZERO;
        BigDecimal amt;
        if ("PERCENT".equalsIgnoreCase(type) || "RATE".equalsIgnoreCase(type)) {
            if (magnitude.compareTo(BigDecimal.ONE) > 0) magnitude = magnitude.divide(HUNDRED, 6, RoundingMode.HALF_UP);
            amt = base.multiply(magnitude);
        } else {
            amt = magnitude;
        }
        amt = money(amt);
        return add ? amt : amt.negate();
    }

    public static BigDecimal signedDiscount(BigDecimal base, String type, BigDecimal value) {
        return signedDiscount(base, type, value, null);
    }

    public static Map<String, BigDecimal> splitTax(BigDecimal inclTax, BigDecimal taxRate) {
        inclTax = money(inclTax);
        taxRate = rate(taxRate);
        BigDecimal denominator = BigDecimal.ONE.add(taxRate);
        BigDecimal excl = inclTax.divide(denominator, 2, RoundingMode.HALF_UP);
        BigDecimal tax = inclTax.subtract(excl);
        Map<String, BigDecimal> r = new LinkedHashMap<>();
        r.put("excl", excl);
        r.put("tax", tax);
        r.put("incl", inclTax);
        return r;
    }

    /**
     * 将一个有符号调整额 signedDelta（减为负、加为正）按各行权重 weight 比例分摊。
     * 只在 totalWeight>0 的行间分摊；“减”不得使任一行低于 0，超出可分摊额度的部分由调用方校验拦截。
     * 返回每行分摊额（有符号，2 位），尾差落入权重最大行。
     */
    public static BigDecimal[] allocate(BigDecimal signedDelta, List<BigDecimal> weights) {
        int n = weights.size();
        BigDecimal[] out = new BigDecimal[n];
        for (int i = 0; i < n; i++) out[i] = BigDecimal.ZERO;
        if (signedDelta == null || signedDelta.signum() == 0) return out;
        BigDecimal totalW = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalW.signum() <= 0) return out;
        BigDecimal delta2 = money(signedDelta);
        int maxIdx = 0;
        BigDecimal maxW = BigDecimal.ZERO;
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal w = money(weights.get(i));
            if (w.compareTo(maxW) >= 0) { maxW = w; maxIdx = i; }
            if (w.signum() <= 0) { out[i] = BigDecimal.ZERO; continue; }
            BigDecimal share = delta2.multiply(w).divide(totalW, 2, RoundingMode.HALF_UP);
            out[i] = share;
            allocated = allocated.add(share);
        }
        BigDecimal diff = delta2.subtract(allocated);
        out[maxIdx] = money(out[maxIdx].add(diff));
        return out;
    }
}
