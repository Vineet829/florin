package com.florin.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.IntegrationTest;
import com.florin.common.SecretTokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class EmailTokenMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void hashesInPlace() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("43");
            JdbcTemplate jdbc = database.jdbc();
            long user = jdbc.queryForObject("""
                    insert into users (id, email, username, first_name, last_name) values (?, ?, ?, 'Old', 'Owner')
                    returning pkid
                    """, Long.class, UUID.randomUUID(), "pending@example.test", "pending");
            String sent = "4f0c2b7e9d1a3c5e7f9b0d2c4e6a8b0c1d3e5f7a9b2c4d6e8f0a1b3c5d7e9f0a";
            jdbc.update("""
                    insert into verify_reset_tokens (id, user_pkid, token, created_at, expires_at)
                    values (?, ?, ?, now(), now() + interval '15 minutes')
                    """, UUID.randomUUID(), user, sent);

            database.migrateToLatest();

            assertThat(jdbc.queryForObject("select token from verify_reset_tokens", String.class))
                    .isEqualTo(SecretTokens.hash(sent))
                    .isNotEqualTo(sent);
        }
    }
}
