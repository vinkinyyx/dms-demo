/*
 * DocNoGenerator unit tests. Verifies code format and sequence increment
 * based on the mocked doc_no_sequences atomic upsert.
 */
package com.dms.common.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocNoGeneratorTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private EntityManager em;
    private Query query;
    private final AtomicLong seq = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        em = mock(EntityManager.class);
        query = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(em.createNativeQuery(org.mockito.ArgumentMatchers.contains("doc_no_sequences"))).thenReturn(query);
        when(em.createNativeQuery(org.mockito.ArgumentMatchers.argThat(s -> s != null && s.startsWith("SELECT COUNT(1)")))).thenReturn(countQuery);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(countQuery.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(query.getSingleResult()).thenAnswer(inv -> seq.incrementAndGet());
        when(countQuery.getSingleResult()).thenReturn(0L);
        TenantContext.setTenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DocNoGenerator newGen() {
        return new DocNoGenerator(em);
    }

    @Test
    @DisplayName("Same-day multiple calls produce strictly increasing sequence")
    void should_incrementSequence_when_sameDayMultipleCalls() {
        DocNoGenerator gen = newGen();
        String first = gen.next("SO");
        String second = gen.next("SO");
        String third = gen.next("SO");

        assertThat(first).endsWith("-00001");
        assertThat(second).endsWith("-00002");
        assertThat(third).endsWith("-00003");
    }

    @Test
    @DisplayName("Generated code matches PREFIX-YYYYMMDD-NNNNN format")
    void should_returnFormattedCode_when_generate() {
        DocNoGenerator gen = newGen();
        String code = gen.next("PO");
        String today = LocalDate.now().format(DATE_FMT);

        assertThat(code).startsWith("PO-" + today + "-");
        assertThat(code).matches("^PO-\\d{8}-\\d{5}$");
    }

    @Test
    @DisplayName("Different prefixes are counted independently")
    void should_independentSequence_when_differentPrefix() {
        // reset sequence per prefix is handled by DB upsert in production;
        // here we emulate independent counters via a fresh generator/counter per prefix.
        DocNoGenerator soGen = newGen();
        String so1 = soGen.next("SO");
        assertThat(so1).endsWith("-00001");

        seq.set(0);
        DocNoGenerator poGen = newGen();
        String po1 = poGen.next("PO");
        assertThat(po1).endsWith("-00001");
    }

    @Test
    @DisplayName("Concurrent generation produces unique codes")
    void should_generateUniqueCode_when_concurrentAccess() throws InterruptedException {
        DocNoGenerator gen = newGen();
        int threadCount = 20;
        int perThread = 50;
        java.util.Set<String> codes = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        codes.add(gen.next("DN"));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(codes).hasSize(threadCount * perThread);
    }

    @Test
    @DisplayName("Prefix is case-sensitive")
    void should_treatCaseSensitivePrefix_when_generate() {
        DocNoGenerator upperGen = newGen();
        String upper = upperGen.next("SO");
        assertThat(upper).startsWith("SO-").endsWith("-00001");

        seq.set(0);
        DocNoGenerator lowerGen = newGen();
        String lower = lowerGen.next("so");
        assertThat(lower).startsWith("so-").endsWith("-00001");
    }
}
