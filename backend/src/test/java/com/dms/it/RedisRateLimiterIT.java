package com.dms.it;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies a real Redis-backed RRateLimiter (the same primitive used by
 * RateLimitInterceptor) enforces its quota across acquisitions. The mocked
 * Redis used by normal tests always returns true, which cannot prove this.
 */
class RedisRateLimiterIT extends RedisIntegrationTestSupport {

    @Test
    void rateLimiterEnforcesQuotaAgainstRealRedis() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + System.getProperty("spring.data.redis.host")
                        + ":" + System.getProperty("spring.data.redis.port"));
        RedissonClient redisson = Redisson.create(config);
        try {
            RRateLimiter limiter = redisson.getRateLimiter("dms:it:ratelimit:smoke");
            limiter.delete();
            limiter.trySetRate(RateType.OVERALL, 3, 60, RateIntervalUnit.SECONDS);

            assertThat(limiter.tryAcquire(1)).isTrue();
            assertThat(limiter.tryAcquire(1)).isTrue();
            assertThat(limiter.tryAcquire(1)).isTrue();
            assertThat(limiter.tryAcquire(1))
                    .as("第四次获取应被真实 Redis 限流拒绝（mock 下永远为 true）")
                    .isFalse();

            limiter.delete();
            redisson.getKeys().flushdb();
        } finally {
            redisson.shutdown();
        }
    }
}
