/*
 * 单据编号生成器，格式 {PREFIX}-YYYYMMDD-{6位0填充自增}，进程内自增序号按日期切换。
 */
package com.dms.common.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DocNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /**
     * 生成单据编号。
     *
     * @param prefix 业务前缀，如 SO / PO / DN
     * @return 完整单据号
     */
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = prefix + "-" + date;
        AtomicLong counter = counters.computeIfAbsent(key, k -> new AtomicLong(0));
        long seq = counter.incrementAndGet();
        return String.format("%s-%s-%06d", prefix, date, seq);
    }
}
