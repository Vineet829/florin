package com.florin.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void clockTicksInMicroseconds() {
        Clock clock = new ClockConfig().clock();

        for (int read = 0; read < 1_000; read++) {
            assertThat(clock.instant().getNano() % 1_000).isZero();
        }
    }
}
