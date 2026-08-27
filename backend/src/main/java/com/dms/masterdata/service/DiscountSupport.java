package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;

final class DiscountSupport {

    private DiscountSupport() {}

    static BigDecimal toRate(Object o, String fieldName) {
        if (o == null || String.valueOf(o).isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, fieldName + "不能为空");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, fieldName + "格式不正确");
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("0.9999")) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, fieldName + "必须在 0 ~ 0.9999 之间（即 0% ~ 99.99%）");
        }
        return rate;
    }

    static LocalDate toDate(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "日期格式不正确，应为 YYYY-MM-DD：" + o);
        }
    }

    static Date sqlDate(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "生效日期不能晚于失效日期");
        }
    }

    static void assertStatus(String status) {
        if (!"active".equals(status) && !"inactive".equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "状态只能为 active/inactive");
        }
    }

    static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    static String strOr(Object o, String def) {
        return o == null || String.valueOf(o).isBlank() ? def : String.valueOf(o).trim();
    }

    static String val(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static String rateText(Object rate) {
        if (rate == null) return "";
        return new BigDecimal(String.valueOf(rate)).multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%";
    }
}
