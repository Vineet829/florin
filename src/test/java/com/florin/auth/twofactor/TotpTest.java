package com.florin.auth.twofactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "at {0}s")
    @CsvSource({
        "59, 94287082",
        "1111111109, 07081804",
        "1111111111, 14050471",
        "1234567890, 89005924",
        "2000000000, 69279037",
        "20000000000, 65353130",
    })
    void rfcVectors(long seconds, String expected) {
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(seconds)), 8)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource(
            value = {"f, MY", "fo, MZXQ", "foo, MZXW6", "foob, MZXW6YQ", "fooba, MZXW6YTB", "foobar, MZXW6YTBOI"})
    void encodesBase32Unpadded(String plain, String expected) {
        assertThat(Totp.base32(plain.getBytes(StandardCharsets.US_ASCII))).isEqualTo(expected);
    }

    @Test
    void acceptsAdjacentStepsOnly() {
        byte[] secret = Totp.newSecret();
        Instant now = Instant.parse("2026-09-30T10:00:15Z");
        long step = Totp.step(now);

        assertThat(Totp.match(secret, Totp.code(secret, step), now)).hasValue(step);
        assertThat(Totp.match(secret, Totp.code(secret, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.match(secret, Totp.code(secret, step + 1), now)).hasValue(step + 1);
        assertThat(Totp.match(secret, Totp.code(secret, step + 2), now)).isEmpty();
        assertThat(Totp.match(secret, "12345", now)).isEmpty();
    }
}
