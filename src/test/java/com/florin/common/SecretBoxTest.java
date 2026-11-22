package com.florin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.florin.config.EncryptionProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretBoxTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTrip() {
        SecretBox box = new SecretBox(new EncryptionProperties(KEY));
        byte[] secret = "a secret".getBytes(StandardCharsets.UTF_8);

        String once = box.seal(secret);
        String twice = box.seal(secret);

        assertThat(box.open(once)).isEqualTo(secret);
        assertThat(once).isNotEqualTo(twice);
    }

    @Test
    void refusesChangedValue() {
        SecretBox box = new SecretBox(new EncryptionProperties(KEY));
        byte[] sealed = Base64.getDecoder().decode(box.seal(new byte[] {1, 2, 3}));
        sealed[sealed.length - 1] ^= 1;

        assertThatThrownBy(() -> box.open(Base64.getEncoder().encodeToString(sealed)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesUnreadableValues() {
        SecretBox box = new SecretBox(new EncryptionProperties(KEY));
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 7);
        String elsewhere = new SecretBox(new EncryptionProperties(Base64.getEncoder().encodeToString(otherKey)))
                .seal(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> box.open(elsewhere)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> box.open(Base64.getEncoder().encodeToString(new byte[20])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> box.open("not-a-sealed-value")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void checksKeyPresenceAndSize() {
        assertThat(new SecretBox(new EncryptionProperties("")).available()).isFalse();
        assertThat(new SecretBox(new EncryptionProperties(null)).available()).isFalse();
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new SecretBox(new EncryptionProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class);
    }
}
