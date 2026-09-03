package com.dms.it.auth;

import com.dms.it.RedisIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke IT proving the full Spring context wires a REAL RedissonClient
 * (connecting to Testcontainers Redis) rather than the mock in BaseIntegrationTest.
 * The logout/token-blacklist and login-rate-limit flows depend on this working
 * against an actual Redis; see AuthService and RateLimitInterceptor.
 */
@SpringBootTest
class AuthLogoutRedisIT extends RedisIntegrationTestSupport {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    void realRedissonBucketPersistsAccessors() {
        String key = "dms:it:blacklist:smoke-token";
        var bucket = redissonClient.getBucket(key);
        bucket.set("1", Duration.ofMinutes(10));

        assertThat(bucket.isExists())
                .as("写入真实 Redis 的黑名单 key 必须能回读（验证 Redis 非 mock）")
                .isTrue();
        assertThat(bucket.get()).isEqualTo("1");

        bucket.delete();
        redissonClient.getKeys().flushdb();
    }
}
