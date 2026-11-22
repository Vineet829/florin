package com.florin.auth;

import com.florin.auth.session.UserSessionRepository;
import com.florin.business.InvitationRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenCleanup {

    private final JdbcTemplate jdbcTemplate;
    private final UserSessionRepository sessionRepository;
    private final InvitationRepository invitationRepository;
    private final Clock clock;

    public TokenCleanup(
            JdbcTemplate jdbcTemplate,
            UserSessionRepository sessionRepository,
            InvitationRepository invitationRepository,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionRepository = sessionRepository;
        this.invitationRepository = invitationRepository;
        this.clock = clock;
    }

    @Transactional
    public int removeExpired() {
        Instant now = Instant.now(clock);
        int removed = jdbcTemplate.update("delete from verify_reset_tokens where expires_at < ?", Timestamp.from(now));
        return removed
                + sessionRepository.deleteEnded(now)
                + invitationRepository.deleteFinished(now.minus(Duration.ofDays(30)));
    }
}
