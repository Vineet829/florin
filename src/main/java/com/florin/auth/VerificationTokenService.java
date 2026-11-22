package com.florin.auth;

import com.florin.common.SecretTokens;
import com.florin.config.AppProperties;
import com.florin.user.User;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationTokenService {

    private final VerifyResetTokenRepository tokenRepository;
    private final Duration tokenTtl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public VerificationTokenService(
            VerifyResetTokenRepository tokenRepository, AppProperties properties, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.tokenTtl = properties.verification().tokenTtl();
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        return issue(user, tokenTtl);
    }

    @Transactional
    public String issue(User user, Duration ttl) {
        byte[] material = new byte[32];
        random.nextBytes(material);
        String token = HexFormat.of().formatHex(material);

        VerifyResetToken entity = new VerifyResetToken();
        entity.setUser(user);
        entity.setToken(SecretTokens.hash(token));
        Instant now = clock.instant();
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(ttl));
        tokenRepository.save(entity);
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<VerifyResetToken> findUsable(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository
                .findByToken(SecretTokens.hash(token))
                .filter(entity -> entity.getExpiresAt().isAfter(clock.instant()));
    }

    @Transactional
    public void consume(VerifyResetToken token) {
        tokenRepository.delete(token);
    }

    @Transactional
    public void revokeAll(User user) {
        tokenRepository.deleteByUserPkid(user.getPkid());
    }
}
