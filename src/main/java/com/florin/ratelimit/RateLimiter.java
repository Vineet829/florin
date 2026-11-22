package com.florin.ratelimit;

import com.florin.metrics.AppMetrics;
import io.github.bucket4j.BucketExceptions.BucketExecutionException;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import java.sql.SQLTransientException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiter.class);

    private final BucketStore buckets;
    private final AppMetrics metrics;

    RateLimiter(BucketStore buckets, AppMetrics metrics) {
        this.buckets = buckets;
        this.metrics = metrics;
    }

    public void consume(RateLimit limit, String key) {
        ConsumptionProbe probe = checked(() -> buckets.bucket(limit, key).tryConsumeAndReturnRemaining(1));
        if (!probe.isConsumed()) {
            throw limited(limit, probe.getNanosToWaitForRefill());
        }
    }

    public void requireAvailable(RateLimit limit, String key) {
        EstimationProbe probe = checked(() -> buckets.bucket(limit, key).estimateAbilityToConsume(1));
        if (!probe.canBeConsumed()) {
            throw limited(limit, probe.getNanosToWaitForRefill());
        }
    }

    public void recordFailure(RateLimit limit, String key) {
        checked(() -> buckets.bucket(limit, key).tryConsume(1));
    }

    public void refund(RateLimit limit, String key) {
        checked(() -> {
            buckets.bucket(limit, key).addTokens(1);
            return null;
        });
    }

    public void reset(RateLimit limit, String key) {
        checked(() -> {
            buckets.reset(limit, key);
            return null;
        });
    }

    public int removeExpired() {
        return buckets.removeExpired();
    }

    private static <T> T checked(Supplier<T> check) {
        try {
            return check.get();
        } catch (BucketExecutionException exception) {
            if (!(exception.getCause() instanceof SQLTransientException busy)) {
                throw exception;
            }
            LOGGER.warn("Rate limits could not be checked: {}", busy.getMessage());
            throw RateLimitedException.busy();
        }
    }

    private RateLimitedException limited(RateLimit limit, long nanosToWait) {
        metrics.rateLimited(limit);
        return new RateLimitedException(limit.reason(), Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L));
    }
}
