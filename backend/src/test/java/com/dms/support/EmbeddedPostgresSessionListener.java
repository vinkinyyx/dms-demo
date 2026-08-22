package com.dms.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts a single embedded PostgreSQL for the whole test JVM and exposes it via
 * system properties. application-test.yml reads DB_HOST/DB_PORT/DB_NAME with
 * defaults; we override those here so every integration test can run without an
 * external database or Docker. Redis is mocked in tests, so we point
 * REDIS_HOST/REDIS_PORT at a dummy value to avoid connection attempts.
 */
public class EmbeddedPostgresSessionListener implements LauncherSessionListener {

    private static EmbeddedPostgres postgres;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (postgres != null) {
            return;
        }
        try {
            Path dataDir = Files.createTempDirectory("dms-embedded-pg-");
            postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(dataDir)
                    .setServerConfig("timezone", "Asia/Shanghai")
                    .start();
            // Append stringtype=unspecified so JSONB entity maps (attrs/quota/modulesEnabled) bind correctly.
            String baseUrl = postgres.getJdbcUrl("postgres", "postgres");
            String jdbcUrl = baseUrl.contains("?") ? baseUrl + "&stringtype=unspecified" : baseUrl + "?stringtype=unspecified";
            int port = postgres.getPort();
            System.setProperty("DB_HOST", "localhost");
            System.setProperty("DB_PORT", String.valueOf(port));
            System.setProperty("DB_NAME", "postgres");
            System.setProperty("DB_USER", "postgres");
            System.setProperty("DB_PASSWORD", "postgres");
            System.setProperty("spring.datasource.url", jdbcUrl);
            System.setProperty("spring.datasource.username", "postgres");
            System.setProperty("spring.datasource.password", "postgres");
            System.setProperty("REDIS_HOST", "localhost");
            System.setProperty("REDIS_PORT", "1");
            System.out.println("[test-env] Embedded PostgreSQL started on port " + port);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start embedded PostgreSQL", e);
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (postgres != null) {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // best effort shutdown
            }
            postgres = null;
        }
    }
}
