package com.dms.v4;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
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
        BigDecimal d;
        if ("RATE".equalsIgnoreCase(type) || "PERCENT".equalsIgnoreCase(type)) {
            if (value.compareTo(BigDecimal.ONE) > 0) value = value.divide(HUNDRED, 4, RoundingMode.HALF_UP);
            d = base.multiply(value);
        } else {
            d = value;
        }
        return money(d.min(base).max(BigDecimal.ZERO));
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
}
