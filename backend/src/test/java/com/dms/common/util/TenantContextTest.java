/*
 * 测试目标：验证 TenantContext 基于 ThreadLocal 的隔离性，含 set/get/clear/多线程。
 * 覆盖用户故事：US-1.4（租户上下文透传）、US-2.5（JWT 认证后写入上下文）。
 */
package com.dms.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("正常流程：set 后 get 返回原值")
    void should_returnValue_when_setThenGet() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(1001L);
        TenantContext.setUsername("alice");

        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
        assertThat(TenantContext.getUserId()).isEqualTo(1001L);
        assertThat(TenantContext.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("正常流程：clear 后所有字段变为 null")
    void should_returnNull_when_afterClear() {
        TenantContext.setTenantId(UUID.randomUUID());
        TenantContext.setUserId(2L);
        TenantContext.setUsername("bob");

        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getUserId()).isNull();
        assertThat(TenantContext.getUsername()).isNull();
    }

    @Test
    @DisplayName("边界：自定义 key 的 set/get 也生效")
    void should_supportGenericKeyValue_when_setCustomKey() {
        TenantContext.set("orgId", 999L);
        assertThat(TenantContext.get("orgId")).isEqualTo(999L);
        assertThat(TenantContext.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("多线程：不同线程持有独立的 ThreadLocal，互不干扰")
    void should_isolateBetweenThreads_when_setInDifferentThreads() throws InterruptedException {
        UUID mainTenant = UUID.randomUUID();
        TenantContext.setTenantId(mainTenant);
        TenantContext.setUserId(1L);

        AtomicReference<UUID> childTenant = new AtomicReference<>();
        AtomicReference<Long> childUser = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        UUID otherTenant = UUID.randomUUID();

        Thread t = new Thread(() -> {
            try {
                // 子线程独立设置
                TenantContext.setTenantId(otherTenant);
                TenantContext.setUserId(2L);
                childTenant.set(TenantContext.getTenantId());
                childUser.set(TenantContext.getUserId());
            } finally {
                TenantContext.clear();
                latch.countDown();
            }
        });
        t.start();
        latch.await();

        assertThat(childTenant.get()).isEqualTo(otherTenant);
        assertThat(childUser.get()).isEqualTo(2L);
        // 主线程的值未被子线程影响
        assertThat(TenantContext.getTenantId()).isEqualTo(mainTenant);
        assertThat(TenantContext.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("正常流程：snapshot 返回当前上下文只读快照")
    void should_returnCopy_when_snapshot() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUsername("carol");

        var snap = TenantContext.snapshot();

        assertThat(snap).containsEntry("tenantId", tenantId);
        assertThat(snap).containsEntry("username", "carol");
        // 改动 snapshot 不影响原上下文
        snap.put("tenantId", UUID.randomUUID());
        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
    }
}
