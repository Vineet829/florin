package com.florin.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public final class Today {

    private static final ZoneId FURTHEST_AHEAD = ZoneId.of("Pacific/Kiritimati");

    private Today() {}

    public static LocalDate latest(Clock clock) {
        return LocalDate.now(clock.withZone(FURTHEST_AHEAD));
    }
}
