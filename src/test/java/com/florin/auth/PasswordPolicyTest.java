package com.florin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Str0ng!pw", "Aa1!aaaa", "ÜberSecret9#"})
    void strongPasswords(String password) {
        assertThat(PasswordPolicy.isStrong(password)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Aa1!aaa", "aa1!aaaa", "AA1!AAAA", "Aa!!aaaa", "Aa11aaaa"})
    void weakPasswords(String password) {
        assertThat(PasswordPolicy.isStrong(password)).isFalse();
    }

    @Test
    void messageNamesEveryRequirement() {
        assertThat(PasswordPolicy.REQUIREMENTS)
                .isEqualTo("Password must be at least 8 characters long, with at least 1 uppercase letter,"
                        + " 1 lowercase letter, 1 number and 1 symbol");
    }

    @Test
    void lengthLimitCountsBytes() {
        String devanagari = Character.toString(0x0915);
        assertThat(PasswordPolicy.fits("a".repeat(72))).isTrue();
        assertThat(PasswordPolicy.fits("a".repeat(73))).isFalse();
        assertThat(PasswordPolicy.fits(devanagari.repeat(24))).isTrue();
        assertThat(PasswordPolicy.fits(devanagari.repeat(25))).isFalse();
        assertThat(PasswordPolicy.fits(null)).isTrue();
    }
}
