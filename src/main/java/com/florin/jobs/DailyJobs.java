package com.florin.jobs;

import com.florin.auth.TokenCleanup;
import com.florin.auth.UnverifiedSignUps;
import com.florin.document.AutomaticReminders;
import com.florin.document.OverdueAlerts;
import com.florin.document.RecurringService;
import com.florin.metrics.AppMetrics;
import com.florin.ratelimit.RateLimiter;
import com.florin.upload.UploadCleanup;
import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyJobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyJobs.class);

    private final RecurringService recurringService;
    private final AutomaticReminders automaticReminders;
    private final OverdueAlerts overdueAlerts;
    private final TokenCleanup tokenCleanup;
    private final UnverifiedSignUps unverifiedSignUps;
    private final RateLimiter rateLimiter;
    private final UploadCleanup uploadCleanup;
    private final AppMetrics metrics;
    private final Clock clock;

    public DailyJobs(
            RecurringService recurringService,
            AutomaticReminders automaticReminders,
            OverdueAlerts overdueAlerts,
            TokenCleanup tokenCleanup,
            UnverifiedSignUps unverifiedSignUps,
            RateLimiter rateLimiter,
            UploadCleanup uploadCleanup,
            AppMetrics metrics,
            Clock clock) {
        this.recurringService = recurringService;
        this.automaticReminders = automaticReminders;
        this.overdueAlerts = overdueAlerts;
        this.tokenCleanup = tokenCleanup;
        this.unverifiedSignUps = unverifiedSignUps;
        this.rateLimiter = rateLimiter;
        this.uploadCleanup = uploadCleanup;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.cron:0 0 7 * * *}", zone = "${app.jobs.zone:UTC}")
    @SchedulerLock(name = "daily-jobs", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    public void run() {
        Instant now = Instant.now(clock);
        int made = step("recurring invoices", () -> recurringService.makeDue(now));
        int reminded = step("automatic reminders", () -> automaticReminders.send(now));
        int alerted = step("overdue alerts", () -> overdueAlerts.raise(now));
        int removed = step("expired tokens", tokenCleanup::removeExpired);
        int signUps = step("unverified accounts", unverifiedSignUps::removeExpired);
        int buckets = step("expired rate limits", rateLimiter::removeExpired);
        int logos = step("unused logos", uploadCleanup::removeUnused);
        LOGGER.info(
                "Daily jobs: {} recurring invoices made, {} reminders sent, {} overdue alerts raised,"
                        + " {} expired tokens, {} unverified accounts, {} expired rate limits and {} unused"
                        + " logos removed",
                made,
                reminded,
                alerted,
                removed,
                signUps,
                buckets,
                logos);
    }

    private int step(String name, IntSupplier job) {
        try {
            return metrics.timeJob(name.replace(' ', '_'), job);
        } catch (RuntimeException exception) {
            LOGGER.error("Daily job step '{}' failed", name, exception);
            return 0;
        }
    }
}
