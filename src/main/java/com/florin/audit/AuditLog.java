package com.florin.audit;

import com.florin.auth.session.CurrentSession;
import com.florin.user.User;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLog {

    static final int POOL_SIZE = 3;
    private static final Duration CONNECTION_WAIT = Duration.ofSeconds(5);
    private static final String INSERT = "insert into audit_events (id, type, business_pkid, user_pkid, actor_pkid,"
            + " email, detail, ip, user_agent, session_id, occurred_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final HikariDataSource pool = new HikariDataSource();
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public AuditLog(JdbcConnectionDetails connection, Clock clock) {
        pool.setPoolName("audit-log");
        pool.setMaximumPoolSize(POOL_SIZE);
        pool.setConnectionTimeout(CONNECTION_WAIT.toMillis());
        pool.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
        pool.addDataSourceProperty("url", connection.getJdbcUrl());
        pool.setUsername(connection.getUsername());
        pool.setPassword(connection.getPassword());
        this.jdbcTemplate = new JdbcTemplate(pool);
        this.clock = clock;
    }

    @PreDestroy
    void close() {
        pool.close();
    }

    public void record(AuditEventType type, User user) {
        record(type, user, null, null, null);
    }

    public void record(AuditEventType type, User user, User actor) {
        record(type, user, actor, null, null);
    }

    public void signInFailed(String email, User user, String reason) {
        record(AuditEventType.SIGN_IN_FAILED, user, null, email, reason);
    }

    public void record(AuditEventType type, User user, User actor, String email, String detail) {
        record(type, user, actor, email, detail, null);
    }

    public void recordForBusiness(AuditEventType type, Long businessPkid, User user, User actor, String detail) {
        save(type, businessPkid, user, actor, null, detail, null);
    }

    public void recordForBusiness(
            AuditEventType type, Long businessPkid, User user, User actor, String email, String detail) {
        save(type, businessPkid, user, actor, email, detail, null);
    }

    public void record(AuditEventType type, User user, User actor, String email, String detail, UUID sessionId) {
        save(type, null, user, actor, email, detail, sessionId);
    }

    private void save(
            AuditEventType type,
            Long businessPkid,
            User user,
            User actor,
            String email,
            String detail,
            UUID sessionId) {
        String address = email != null ? email : user == null ? null : user.getEmail();
        String ip = null;
        String userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            ip = request.getRemoteAddr();
            userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        }
        jdbcTemplate.update(
                INSERT,
                UUID.randomUUID(),
                type.name(),
                businessPkid,
                user == null ? null : user.getPkid(),
                actor == null ? null : actor.getPkid(),
                clip(address == null ? null : address.trim().toLowerCase(Locale.ROOT), 254),
                clip(detail, 255),
                clip(ip, 64),
                clip(userAgent, 255),
                sessionId != null ? sessionId : CurrentSession.id().orElse(null),
                clock.instant().atOffset(ZoneOffset.UTC));
    }

    private static String clip(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
