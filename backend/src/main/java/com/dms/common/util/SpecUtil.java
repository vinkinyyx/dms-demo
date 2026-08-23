/*
 * 通用列表过滤 Specification 构造器（v4.2.4）
 * 支持：租户隔离 + 任意实体字段的等值/模糊过滤 + 数值/日期范围过滤。
 *   - 字符串字段：ILIKE %value%（大小写不敏感）
 *   - 其它字段（数值/布尔/枚举）：等值匹配
 *   - 范围过滤：参数 key + "From" / key + "To"（数值 >= / <=；日期/时间 >= / <）
 * 前端把激活的列筛选以 field=value 形式作为查询参数传来，跨全部数据在数据库层过滤。
 */
package com.dms.common.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpecUtil {

    private static final Set<String> RESERVED = Set.of("page", "size", "sort", "keyword", "kw");

    private SpecUtil() {}

    public static <T> Specification<T> byTenantAndFilters(UUID tenantId, Map<String, String> params) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (tenantId != null) {
                try { ps.add(cb.equal(root.get("tenantId"), tenantId)); } catch (Exception ignored) {}
            }
            if (params != null) {
                String keyword = params.get("keyword");
                if (keyword == null || keyword.isBlank()) keyword = params.get("kw");
                if (keyword != null && !keyword.isBlank()) {
                    String[] tokens = keyword.trim().split("[\s,，]+");
                    for (String token : tokens) {
                        if (token == null || token.isBlank()) continue;
                        String kw = "%" + token.trim().toLowerCase() + "%";
                        List<Predicate> ors = new ArrayList<>();
                        for (String fld : new String[]{"code", "name", "nameCn", "nameEn", "spec"}) {
                            try {
                                var p = root.get(fld);
                                if (p.getJavaType() == String.class) {
                                    ors.add(cb.like(cb.lower(p.as(String.class)), kw));
                                }
                            } catch (Exception ignored) {}
                        }
                        if (!ors.isEmpty()) ps.add(cb.or(ors.toArray(new Predicate[0])));
                    }
                }
                // 记录已被 From/To 范围消费的字段名，避免再被主循环用 equal 重复加谓词
                Set<String> rangeConsumed = new HashSet<>();
                for (Map.Entry<String, String> e : params.entrySet()) {
                    String key = e.getKey();
                    String val = e.getValue();
                    if (key == null || val == null || val.isBlank() || RESERVED.contains(key)) continue;
                    if (key.endsWith("From") || key.endsWith("To")) {
                        String field = key.substring(0, key.length() - (key.endsWith("From") ? 4 : 2));
                        try {
                            Path<?> path = root.get(field);
                            Class<?> type = path.getJavaType();
                            boolean isFrom = key.endsWith("From");
                            Predicate p = buildRangePredicate(cb, path, type, isFrom, val);
                            if (p != null) {
                                ps.add(p);
                                rangeConsumed.add(field);
                            }
                        } catch (IllegalArgumentException notAField) {
                            // 非实体字段（如无关参数）忽略
                        } catch (Exception ignored) {
                        }
                        continue;
                    }
                    if (rangeConsumed.contains(key)) continue;
                    try {
                        var path = root.get(key);
                        Class<?> type = path.getJavaType();
                        if (type == String.class) {
                            ps.add(cb.like(cb.lower(path.as(String.class)), "%" + val.trim().toLowerCase() + "%"));
                        } else if (type == Boolean.class || type == boolean.class) {
                            ps.add(cb.equal(path, Boolean.valueOf(val.trim())));
                        } else if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
                            ps.add(cb.equal(path.as(String.class), val.trim()));
                        } else {
                            ps.add(cb.equal(path, val.trim()));
                        }
                    } catch (IllegalArgumentException notAField) {
                        // 非实体字段（如无关参数）忽略
                    } catch (Exception ignored) {
                    }
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** 把列筛选的日期/日期时间字符串解析为范围边界 Timestamp。
     *  isFrom=true：日期取当天 00:00:00，日期时间取原值（配合 >=）
     *  isFrom=false：日期取次日 00:00:00（配合 <），日期时间取原值（配合 <=）
     *  解析失败返回 null。 */
    public static java.sql.Timestamp rangeBound(String v, boolean isFrom) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        try {
            if (s.length() > 10) return java.sql.Timestamp.valueOf(LocalDateTime.parse(s.replace(' ', 'T')));
            LocalDate d = LocalDate.parse(s);
            return java.sql.Timestamp.valueOf(isFrom ? d.atStartOfDay() : d.plusDays(1).atStartOfDay());
        } catch (Exception e) { return null; }
    }

    /** 值是否包含时间部分（YYYY-MM-DD HH:mm:ss）。 */
    public static boolean hasTime(String v) {
        return v != null && v.trim().length() > 10;
    }

    private static LocalDateTime parseLocalDateTime(String v) {
        String s = v.trim().replace(' ', 'T');
        if (s.length() <= 10) return LocalDate.parse(s).atStartOfDay();
        return LocalDateTime.parse(s);
    }

    private static OffsetDateTime parseOffsetDateTime(String v) {
        String s = v.trim().replace(' ', 'T');
        if (s.length() <= 10) return LocalDate.parse(s).atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        try { return OffsetDateTime.parse(s); }
        catch (DateTimeParseException e) { return LocalDateTime.parse(s).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(); }
    }

    /**
     * 构造范围 Predicate。返回 null 表示该类型不支持范围。
     *   - BigDecimal：>= / <=
     *   - 其它 Number / 基本类型：按字符串比较（>= / <=），与现有数字 equal 行为保持一致
     *   - LocalDate：>= / <（To 用次日 0 点）
     *   - LocalDateTime / OffsetDateTime：>= / <（To 用次日）
     *   - java.util.Date：>= / <（按 UTC 日界）
     *   - 字符串 / 布尔 / 其它：返回 null
     */
    private static Predicate buildRangePredicate(CriteriaBuilder cb,
                                                  Path<?> path,
                                                  Class<?> type,
                                                  boolean isFrom,
                                                  String rawVal) {
        String v = rawVal == null ? "" : rawVal.trim();
        if (v.isEmpty()) return null;
        if (type == BigDecimal.class) {
            BigDecimal n;
            try { n = new BigDecimal(v); } catch (NumberFormatException e) { return null; }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Path<BigDecimal> p = (Path) path;
            return isFrom ? cb.greaterThanOrEqualTo(p, n) : cb.lessThanOrEqualTo(p, n);
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return isFrom
                ? cb.greaterThanOrEqualTo(path.as(String.class), v)
                : cb.lessThanOrEqualTo(path.as(String.class), v);
        }
        if (type == LocalDate.class) {
            LocalDate d;
            try { d = LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v); }
            catch (DateTimeParseException e) { return null; }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Path<LocalDate> p = (Path) path;
            return isFrom ? cb.greaterThanOrEqualTo(p, d) : cb.lessThan(p, d.plusDays(1));
        }
        if (type == LocalDateTime.class) {
            LocalDateTime t;
            try { t = parseLocalDateTime(v); } catch (DateTimeParseException e) { return null; }
            boolean dateOnly = v.trim().length() <= 10;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Path<LocalDateTime> p = (Path) path;
            return isFrom ? cb.greaterThanOrEqualTo(p, t) : (dateOnly ? cb.lessThan(p, t.plusDays(1)) : cb.lessThanOrEqualTo(p, t));
        }
        if (type == OffsetDateTime.class) {
            OffsetDateTime t;
            try { t = parseOffsetDateTime(v); } catch (DateTimeParseException e) { return null; }
            boolean dateOnly = v.trim().length() <= 10;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Path<OffsetDateTime> p = (Path) path;
            return isFrom ? cb.greaterThanOrEqualTo(p, t) : (dateOnly ? cb.lessThan(p, t.plusDays(1)) : cb.lessThanOrEqualTo(p, t));
        }
        if (type == Date.class) {
            LocalDate d;
            try { d = LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v); }
            catch (DateTimeParseException e) { return null; }
            Date from = Date.from(d.atStartOfDay(ZoneOffset.UTC).toInstant());
            Date to = Date.from(d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            @SuppressWarnings({"unchecked", "rawtypes"})
            Path<Date> p = (Path) path;
            return isFrom ? cb.greaterThanOrEqualTo(p, from) : cb.lessThan(p, to);
        }
        return null;
    }
}
