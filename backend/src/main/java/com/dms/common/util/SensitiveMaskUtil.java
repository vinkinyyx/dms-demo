package com.dms.common.util;

public final class SensitiveMaskUtil {
    private SensitiveMaskUtil() {}

    public static String phone(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() < 7) return "***";
        return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
    }

    public static String email(String value) {
        if (value == null || !value.contains("@")) return value;
        String[] parts = value.split("@", 2);
        String name = parts[0];
        if (name.length() <= 2) return name.charAt(0) + "***@" + parts[1];
        return name.substring(0, 2) + "***@" + parts[1];
    }

    public static String name(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() == 1) return value;
        if (value.length() == 2) return value.charAt(0) + "*";
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    public static String idCard(String value) {
        if (value == null || value.length() < 8) return "***";
        return value.substring(0, 4) + "**********" + value.substring(value.length() - 4);
    }
}
