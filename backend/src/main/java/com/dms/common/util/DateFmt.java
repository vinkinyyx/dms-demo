/*
 * 时间格式化工具：把原生 SQL 查询（java.sql.Timestamp / OffsetDateTime / DATE 等）
 * 的时间对象统一按北京时区（Asia/Shanghai）格式化为字符串，
 * 与 JVM 默认时区无关，解决原生 SQL 路径绕过 Jackson 时区配置导致时间非北京时间的问题。
 */
package com.dms.common.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateFmt {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateFmt() {}

    /** 格式化日期时间（带时区换算到北京时间）；DATE 类型只输出到日。null 返回 null。 */
    public static String fmt(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof Timestamp) {
                return DT.format(((Timestamp) v).toInstant().atZone(ZONE));
            }
            if (v instanceof OffsetDateTime) {
                return DT.format(((OffsetDateTime) v).atZoneSameInstant(ZONE));
            }
            if (v instanceof Instant) {
                return DT.format(((Instant) v).atZone(ZONE));
            }
            if (v instanceof LocalDateTime) {
                return DT.format(((LocalDateTime) v).atZone(ZONE));
            }
            if (v instanceof java.sql.Date) {
                return ((java.sql.Date) v).toLocalDate().format(D);
            }
            if (v instanceof LocalDate) {
                return ((LocalDate) v).format(D);
            }
        } catch (Exception ignored) {
        }
        return String.valueOf(v);
    }
}
