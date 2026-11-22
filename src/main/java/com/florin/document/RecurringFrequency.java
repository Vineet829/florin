package com.florin.document;

import java.time.LocalDate;

public enum RecurringFrequency {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    QUARTERLY("quarterly"),
    YEARLY("yearly");

    private final String label;

    RecurringFrequency(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    LocalDate run(LocalDate start, int runs) {
        return switch (this) {
            case WEEKLY -> start.plusWeeks(runs);
            case MONTHLY -> start.plusMonths(runs);
            case QUARTERLY -> start.plusMonths(3L * runs);
            case YEARLY -> start.plusYears(runs);
        };
    }
}
