/*
 * 销退明细行纯逻辑支持：负责行字段解析、数量聚合、单价/金额计算。
 * 从 SalesReturnController 下沉，便于毫秒级单元测试，不直接访问数据库。
 */
package com.dms.order.service;

import com.dms.v4.V4Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SalesReturnLineSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    public BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    public String strOr(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }

    @SafeVarargs
    public final Long firstLong(Map<String, Object> body, String... keys) {
        if (body == null || keys == null) return null;
        for (String k : keys) {
            Long v = toLong(body.get(k));
            if (v != null) return v;
        }
        return null;
    }

    @SafeVarargs
    public final String firstString(Map<String, Object> body, String... keys) {
        if (body == null || keys == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    public Long jsonLong(Object extra, String key) {
        if (extra == null) return null;
        try {
            Map<?, ?> m = MAPPER.readValue(String.valueOf(extra), Map.class);
            Object v = m.get(key);
            return v == null ? null : Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    public Long sourceOutLineId(Object extra, Object fallback) {
        if (extra != null) {
            try {
                Map<?, ?> m = (extra instanceof Map<?, ?> mp) ? mp : MAPPER.readValue(String.valueOf(extra), Map.class);
                Object v = m.get("sourceOutLineId");
                if (v != null) return Long.valueOf(String.valueOf(v));
            } catch (Exception ignored) {
            }
        }
        return toLong(fallback);
    }

    public Long lineSourceOutLineId(Map<String, Object> line) {
        Long id = toLong(line.get("sourceOutLineId"));
        return id != null ? id : toLong(line.get("id"));
    }

    /**
     * 聚合请求的销退明细：校验行非空、原出库行存在、数量为正，并按原出库行汇总数量。
     */
    public Aggregation aggregate(List<Map<String, Object>> lines) {
        if (lines == null || lines.isEmpty()) {
            return Aggregation.error("请添加销退明细");
        }
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> line = lines.get(i);
            Long sourceOutLineId = lineSourceOutLineId(line);
            if (sourceOutLineId == null) {
                return Aggregation.error("第 " + (i + 1) + " 行缺少原出库行");
            }
            BigDecimal qty = toBd(line.get("qty"));
            if (qty.signum() <= 0) {
                return Aggregation.error("第 " + (i + 1) + " 行退货数量必须大于0");
            }
            quantities.merge(sourceOutLineId, qty, BigDecimal::add);
        }
        return Aggregation.ok(quantities);
    }

    public BigDecimal resolveUnitPrice(BigDecimal shippedQty, BigDecimal sourceFinalAmount, Object fallbackUnitPrice) {
        BigDecimal shipped = toBd(shippedQty);
        BigDecimal amount = toBd(sourceFinalAmount);
        if (shipped.signum() > 0) {
            return amount.divide(shipped, 4, RoundingMode.HALF_UP);
        }
        return toBd(fallbackUnitPrice);
    }

    public BigDecimal calcLineTotal(BigDecimal qty, BigDecimal unitPrice) {
        return V4Money.money(toBd(qty).multiply(toBd(unitPrice)));
    }

    public record Aggregation(String error, Map<Long, BigDecimal> quantities) {
        static Aggregation error(String error) { return new Aggregation(error, Map.of()); }
        static Aggregation ok(Map<Long, BigDecimal> quantities) { return new Aggregation(null, quantities); }
        public boolean hasError() { return error != null; }
    }
}