package com.florin.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.florin.IntegrationTest;
import com.florin.auth.TokenCleanup;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.jobs.enabled=true", "app.jobs.cron=-"})
class DailyJobsLockIntegrationTest extends IntegrationTest {

    @Autowired
    private DailyJobs dailyJobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TokenCleanup tokenCleanup;

    @BeforeEach
    void freeTheLock() {
        jdbcTemplate.update("update shedlock set lock_until = timezone('utc', now())");
    }

    @Test
    void lockTable() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name || ' ' || data_type from information_schema.columns
                where table_name = 'shedlock' order by ordinal_position
                """, String.class);

        assertThat(columns).containsExactly(
                "name character varying",
                "lock_until timestamp without time zone",
                "locked_at timestamp without time zone",
                "locked_by character varying");
    }

    @Test
    void skipsWhileLockIsHeld() {
        jdbcTemplate.update("""
                insert into shedlock (name, lock_until, locked_at, locked_by)
                values ('daily-jobs', timezone('utc', now()) + interval '1 hour', timezone('utc', now()), 'other')
                on conflict (name) do update
                set lock_until = excluded.lock_until, locked_at = excluded.locked_at, locked_by = excluded.locked_by
                """);

        dailyJobs.run();
        verify(tokenCleanup, never()).removeExpired();

        freeTheLock();
        dailyJobs.run();
        verify(tokenCleanup).removeExpired();
    }

    @Test
    void runsOncePerTick() {
        dailyJobs.run();
        dailyJobs.run();

        verify(tokenCleanup).removeExpired();
    }
}
