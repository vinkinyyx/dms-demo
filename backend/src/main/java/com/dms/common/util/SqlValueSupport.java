package com.dms.common.util;

import java.math.BigDecimal;

public final class SqlValueSupport {
    private SqlValueSupport() {}

    public static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static BigDecimal toBdZero(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public static String strOr(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}