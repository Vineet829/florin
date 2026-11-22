package com.florin.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.IntegrationTest;
import com.florin.common.SecretTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class RateLimitIdsMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void forgetsUnkeyedIds() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("57");
            JdbcTemplate jdbc = database.jdbc();
            jdbc.update(
                    "insert into rate_limit_buckets (id, state, expires_at) values (?, ?, ?)",
                    "SIGN_UP:" + SecretTokens.hash("203.0.113.7"),
                    new byte[] {1, 2, 3},
                    System.currentTimeMillis() + 60_000);
            jdbc.update(
                    "insert into rate_limit_buckets (id, state, expires_at) values (?, null, null)",
                    "FAILED_SIGN_IN:" + SecretTokens.hash("203.0.113.7|neha@example.test"));

            database.migrateToLatest();

            assertThat(jdbc.queryForObject("select count(*) from rate_limit_buckets", Integer.class)).isZero();
        }
    }
}
