package com.florin.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.florin.metrics.AppMetrics;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketExceptions.BucketExecutionException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RateLimiterTest {

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-09-30T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RateLimiter limiter = new RateLimiter(new LocalBucketStore(clock), new AppMetrics(registry));

    @Test
    void limitsAndRefills() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.consume(RateLimit.SIGN_UP, "10.0.0.1");
        }

        assertThatThrownBy(() -> limiter.consume(RateLimit.SIGN_UP, "10.0.0.1"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessage("Too many sign-ups from this network. Please try again in 12 minutes.")
                .extracting(e -> ((RateLimitedException) e).getRetryAfterSeconds())
                .isEqualTo(720L);
        assertThat(registry.get("invoicespot.ratelimit.rejections").tag("limit", "sign_up").counter().count())
                .isEqualTo(1);
        assertThatCode(() -> limiter.consume(RateLimit.SIGN_UP, "10.0.0.2")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(12));
        assertThatCode(() -> limiter.consume(RateLimit.SIGN_UP, "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void onlyFailuresCount() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.requireAvailable(RateLimit.FAILED_SIGN_IN, "ip|neha");
            limiter.recordFailure(RateLimit.FAILED_SIGN_IN, "ip|neha");
        }
        assertThatThrownBy(() -> limiter.requireAvailable(RateLimit.FAILED_SIGN_IN, "ip|neha"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageStartingWith("Too many sign-in attempts. Please try again in 3 minutes");

        limiter.reset(RateLimit.FAILED_SIGN_IN, "ip|neha");
        assertThatCode(() -> limiter.requireAvailable(RateLimit.FAILED_SIGN_IN, "ip|neha")).doesNotThrowAnyException();
    }

    @Test
    void shortWaitsInSeconds() {
        assertThat(new RateLimitedException("Slow down", 1).getMessage()).isEqualTo("Slow down. Please try again in 1 second.");
        assertThat(new RateLimitedException("Slow down", 42).getMessage()).isEqualTo("Slow down. Please try again in 42 seconds.");
        assertThat(new RateLimitedException("Slow down", 61).getMessage()).isEqualTo("Slow down. Please try again in 2 minutes.");
    }

    private static BucketStore failing(SQLException cause) {
        return new BucketStore() {
            @Override
            public Bucket bucket(RateLimit limit, String key) {
                throw new BucketExecutionException(cause);
            }

            @Override
            public void reset(RateLimit limit, String key) {
                throw new BucketExecutionException(cause);
            }

            @Override
            public int removeExpired() {
                return 0;
            }
        };
    }

    @Test
    void busyPoolAsksToRetry() {
        RateLimiter busy = new RateLimiter(
                failing(new SQLTransientConnectionException("rate-limits - Connection is not available")),
                new AppMetrics(registry));

        for (Runnable check : new Runnable[] {
                () -> busy.consume(RateLimit.SIGN_UP, "10.0.0.1"),
                () -> busy.requireAvailable(RateLimit.FAILED_SIGN_IN, "ip|neha"),
                () -> busy.recordFailure(RateLimit.FAILED_SIGN_IN, "ip|neha"),
                () -> busy.reset(RateLimit.FAILED_SIGN_IN, "ip|neha")}) {
            assertThatThrownBy(check::run)
                    .isInstanceOfSatisfying(RateLimitedException.class, refusal -> {
                        assertThat(refusal.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(refusal.getRetryAfterSeconds()).isEqualTo(5L);
                    })
                    .hasMessage("Too many requests right now. Please try again in 5 seconds.");
        }
    }

    @Test
    void otherFailuresStayErrors() {
        RateLimiter broken = new RateLimiter(
                failing(new SQLException("relation \"rate_limit_buckets\" does not exist")), new AppMetrics(registry));

        assertThatThrownBy(() -> broken.consume(RateLimit.SIGN_UP, "10.0.0.1"))
                .isInstanceOf(BucketExecutionException.class);
    }
}
