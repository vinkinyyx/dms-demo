/*
 * 测试目标：验证 DocNoGenerator 单据编号生成规则。
 * 覆盖用户故事：US-1.6（编号规则统一）、US-4.1（订单创建单据号生成）。
 */
package com.dms.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocNoGenerator 单元测试。
 */
class DocNoGeneratorTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Test
    @DisplayName("正常流程：同日多次自增，序号严格递增")
    void should_incrementSequence_when_sameDayMultipleCalls() {
        DocNoGenerator gen = new DocNoGenerator();
        String first = gen.next("SO");
        String second = gen.next("SO");
        String third = gen.next("SO");

        assertThat(first).endsWith("-000001");
        assertThat(second).endsWith("-000002");
        assertThat(third).endsWith("-000003");
    }

    @Test
    @DisplayName("正常流程：编号格式符合 {PREFIX}-YYYYMMDD-6位序号")
    void should_returnFormattedCode_when_generate() {
        DocNoGenerator gen = new DocNoGenerator();
        String code = gen.next("PO");
        String today = LocalDate.now().format(DATE_FMT);

        assertThat(code)
                .startsWith("PO-" + today + "-")
                .hasSize(3 + 8 + 6 + 2);
        assertThat(code).matches("^PO-\\d{8}-\\d{6}$");
    }

    @Test
    @DisplayName("边界：不同前缀独立计数，互不影响")
    void should_independentSequence_when_differentPrefix() {
        DocNoGenerator gen = new DocNoGenerator();
        String so1 = gen.next("SO");
        String po1 = gen.next("PO");
        String so2 = gen.next("SO");
        String po2 = gen.next("PO");

        assertThat(so1).endsWith("-000001");
        assertThat(so2).endsWith("-000002");
        assertThat(po1).endsWith("-000001");
        assertThat(po2).endsWith("-000002");
    }

    @Test
    @DisplayName("边界：多线程并发生成，编号唯一无重复")
    void should_generateUniqueCode_when_concurrentAccess() throws InterruptedException {
        DocNoGenerator gen = new DocNoGenerator();
        int threadCount = 20;
        int perThread = 50;
        Set<String> codes = new HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        String c = gen.next("DN");
                        synchronized (codes) {
                            codes.add(c);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(codes).hasSize(threadCount * perThread);
    }

    @Test
    @DisplayName("正常流程：前缀区分大小写")
    void should_treatCaseSensitivePrefix_when_generate() {
        DocNoGenerator gen = new DocNoGenerator();
        String upper = gen.next("SO");
        String lower = gen.next("so");
        // 不同 key -> 序号都是 000001
        assertThat(upper).endsWith("-000001");
        assertThat(lower).endsWith("-000001");
        assertThat(upper).startsWith("SO-");
        assertThat(lower).startsWith("so-");
    }
}
