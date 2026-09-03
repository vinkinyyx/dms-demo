package com.dms.it;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base for full-stack integration tests that MUST exercise a REAL Redis
 * (rate limiting, token blacklist, distributed state). PostgreSQL is still the
 * zonky in-JVM embedded Postgres started by {@code EmbeddedPostgresSessionListener}
 * (no Docker needed for the DB); only Redis is provided through Testcontainers.
 *
 * <p>Tests self-skip with an assumption failure when Docker is not available, so
 * {@code mvn verify} stays green on a bare dev box. CI / the deploy host must run
 * Docker so these tests actually execute (the Playwright E2E against the live test
 * environment covers the same paths end-to-end as a second safety net).
 */
public abstract class RedisIntegrationTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @BeforeAll
    static void startRealRedis() {
        assumeTrue(dockerAvailable(),
                "Docker 不可用，跳过需要真实 Redis 的 *IT 集成测试（CI/部署机需提供 Docker）");
        if (!REDIS.isRunning()) {
            REDIS.start();
            // application-test.yml / RedisConfig read spring.data.redis.host|port, which are
            // resolved from REDIS_HOST/REDIS_PORT env placeholders. System properties win.
            System.setProperty("REDIS_HOST", REDIS.getHost());
            System.setProperty("REDIS_PORT", String.valueOf(REDIS.getMappedPort(6379)));
            System.setProperty("spring.data.redis.host", REDIS.getHost());
            System.setProperty("spring.data.redis.port", String.valueOf(REDIS.getMappedPort(6379)));
            System.out.println("[test-env] Testcontainers Redis started at "
                    + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
