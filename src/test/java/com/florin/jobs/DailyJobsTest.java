package com.florin.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.florin.auth.TokenCleanup;
import com.florin.auth.UnverifiedSignUps;
import com.florin.document.AutomaticReminders;
import com.florin.document.OverdueAlerts;
import com.florin.document.RecurringService;
import com.florin.metrics.AppMetrics;
import com.florin.ratelimit.RateLimiter;
import com.florin.upload.UploadCleanup;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

@ExtendWith(MockitoExtension.class)
class DailyJobsTest {

    private static final Instant NOW = Instant.parse("2026-09-30T07:00:00Z");

    @Mock private RecurringService recurringService;
    @Mock private AutomaticReminders automaticReminders;
    @Mock private OverdueAlerts overdueAlerts;
    @Mock private TokenCleanup tokenCleanup;
    @Mock private UnverifiedSignUps unverifiedSignUps;
    @Mock private RateLimiter rateLimiter;
    @Mock private UploadCleanup uploadCleanup;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JobsConfig.class)
            .withBean(DailyJobs.class, () -> new DailyJobs(
                    recurringService,
                    automaticReminders,
                    overdueAlerts,
                    tokenCleanup,
                    unverifiedSignUps,
                    rateLimiter,
                    uploadCleanup,
                    new AppMetrics(new SimpleMeterRegistry()),
                    Clock.systemUTC()));

    private static List<String> cronTasks(ApplicationContext context) {
        return context.getBean(ScheduledTaskHolder.class).getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .toList();
    }

    @Test
    void everyStepRuns() {
        when(recurringService.makeDue(NOW)).thenThrow(new IllegalStateException("database is down"));
        when(automaticReminders.send(NOW)).thenReturn(2);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AppMetrics metrics = new AppMetrics(registry);
        new DailyJobs(
                        recurringService,
                        automaticReminders,
                        overdueAlerts,
                        tokenCleanup,
                        unverifiedSignUps,
                        rateLimiter,
                        uploadCleanup,
                        metrics,
                        clock)
                .run();

        verify(recurringService).makeDue(NOW);
        verify(automaticReminders).send(NOW);
        verify(overdueAlerts).raise(NOW);
        verify(tokenCleanup).removeExpired();
        verify(unverifiedSignUps).removeExpired();
        verify(rateLimiter).removeExpired();
        verify(uploadCleanup).removeUnused();
        assertThat(registry.get("invoicespot.jobs").timers())
                .extracting(timer -> timer.getId().getTag("step") + " " + timer.getId().getTag("outcome"))
                .containsExactlyInAnyOrder(
                        "recurring_invoices failure",
                        "automatic_reminders success",
                        "overdue_alerts success",
                        "expired_tokens success",
                        "unverified_accounts success",
                        "expired_rate_limits success",
                        "unused_logos success");
    }

    @Test
    void runsDailyAtSetTime() {
        runner.run(context -> assertThat(cronTasks(context)).containsExactly("0 0 7 * * *"));
        runner.withPropertyValues("app.jobs.cron=0 30 6 * * *")
                .run(context -> assertThat(cronTasks(context)).containsExactly("0 30 6 * * *"));
        runner.withPropertyValues("app.jobs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledTaskHolder.class));
    }
}
