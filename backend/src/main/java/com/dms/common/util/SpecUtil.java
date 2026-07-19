/*
 * 通用列表过滤 Specification 构造器（v3.4.14）
 * 支持：租户隔离 + 任意实体字段的等值/模糊过滤。
 *   - 字符串字段：ILIKE %value%（大小写不敏感）
 *   - 其它字段（数值/布尔/枚举）：等值匹配
 * 前端把激活的列筛选以 field=value 形式作为查询参数传来，跨全部数据在数据库层过滤。
 */
package com.dms.common.util;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
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
                for (Map.Entry<String, String> e : params.entrySet()) {
                    String key = e.getKey();
                    String val = e.getValue();
                    if (key == null || val == null || val.isBlank() || RESERVED.contains(key)) continue;
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
}
