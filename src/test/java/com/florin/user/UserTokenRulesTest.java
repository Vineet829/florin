package com.florin.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTokenRulesTest {

    private static final Instant CHANGED = Instant.parse("2026-09-30T10:00:00.700Z");

    private static User user(boolean active, Instant passwordChangedAt) {
        User user = new User();
        user.setActive(active);
        user.setPasswordChangedAt(passwordChangedAt);
        return user;
    }

    @Test
    void noPasswordChange() {
        assertThat(user(true, null).acceptsTokenIssuedAt(Instant.EPOCH)).isTrue();
    }

    @Test
    void passwordChange() {
        User user = user(true, CHANGED);

        assertThat(user.acceptsTokenIssuedAt(Instant.parse("2026-09-30T09:59:59Z"))).isFalse();
        assertThat(user.acceptsTokenIssuedAt(Instant.parse("2026-09-30T10:00:00Z"))).isTrue();
        assertThat(user.acceptsTokenIssuedAt(Instant.parse("2026-09-30T10:00:01Z"))).isTrue();
    }

    @Test
    void deactivatedUserRefusesTokens() {
        assertThat(user(false, null).acceptsTokenIssuedAt(Instant.now())).isFalse();
    }
}
