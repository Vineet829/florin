package com.florin.ratelimit;

import com.zaxxer.hikari.HikariDataSource;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import io.github.bucket4j.postgresql.PostgreSQLSelectForUpdateBasedProxyManager;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;

class PostgresBucketStore implements BucketStore, AutoCloseable {

    static final int POOL_SIZE = 4;
    private static final Duration CONNECTION_WAIT = Duration.ofSeconds(2);
    private static final Duration KEEP_FULL = Duration.ofMinutes(5);
    private static final int CLEANUP_BATCH = 1_000;
    private static final String HMAC = "HmacSHA256";
    private static final String ID_KEY_LABEL = "invoicespot rate-limit bucket ids";

    private final HikariDataSource pool = new HikariDataSource();
    private final PostgreSQLSelectForUpdateBasedProxyManager<String> buckets;
    private final SecretKeySpec idKey;

    PostgresBucketStore(JdbcConnectionDetails connection, Clock clock, String secret) {
        idKey = new SecretKeySpec(
                hmac(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC), ID_KEY_LABEL), HMAC);
        pool.setPoolName("rate-limits");
        pool.setMaximumPoolSize(POOL_SIZE);
        pool.setConnectionTimeout(CONNECTION_WAIT.toMillis());
        pool.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
        pool.addDataSourceProperty("url", connection.getJdbcUrl());
        pool.setUsername(connection.getUsername());
        pool.setPassword(connection.getPassword());
        buckets = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(pool)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("rate_limit_buckets")
                .idColumn("id")
                .stateColumn("state")
                .expiresAtColumn("expires_at")
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEEP_FULL))
                .clientClock(BucketStore.timeMeter(clock))
                .build();
    }

    @Override
    public Bucket bucket(RateLimit limit, String key) {
        return buckets.builder()
                .withImplicitConfigurationReplacement(limit.version(), TokensInheritanceStrategy.RESET)
                .build(id(limit, key), () -> BucketConfiguration.builder().addLimit(limit.bandwidth()).build());
    }

    @Override
    public void reset(RateLimit limit, String key) {
        buckets.removeProxy(id(limit, key));
    }

    @Override
    public int removeExpired() {
        int total = 0;
        int removed;
        do {
            removed = buckets.removeExpired(CLEANUP_BATCH);
            total += removed;
        } while (removed == CLEANUP_BATCH);
        return total;
    }

    @Override
    public void close() {
        pool.close();
    }

    String id(RateLimit limit, String key) {
        return limit.name() + ":" + HexFormat.of().formatHex(hmac(idKey, key));
    }

    private static byte[] hmac(SecretKeySpec key, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
