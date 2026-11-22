package com.florin.document;

import java.time.LocalDate;
import java.util.Locale;

public enum DueWeek {
    THIS_WEEK("this-week", 0),
    NEXT_WEEK("next-week", 1),
    WEEK_AFTER("week-after", 2),
    LATER("later", 3),
    CURRENT("current", 0);

    private final String value;
    private final int weeksAhead;

    DueWeek(String value, int weeksAhead) {
        this.value = value;
        this.weeksAhead = weeksAhead;
    }

    public static DueWeek fromValue(String value) {
        for (DueWeek week : values()) {
            if (week.value.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return week;
            }
        }
        throw new IllegalArgumentException("Unknown due week: " + value);
    }

    LocalDate firstDay(LocalDate today) {
        return today.plusWeeks(weeksAhead);
    }

    boolean runsOn() {
        return this == LATER || this == CURRENT;
    }
}
