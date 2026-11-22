package com.florin.common;

import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public record RecentPeriod(String key, LocalDate from, LocalDate to) {

    public static RecentPeriod of(String key, LocalDate today) {
        String value = key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "30d" -> new RecentPeriod(value, today.minusDays(29), today);
            case "90d" -> new RecentPeriod(value, today.minusDays(89), today);
            case "12m" -> new RecentPeriod(value, monthsBack(today, 12), today);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose 30 days, 90 days or 12 months");
        };
    }

    public static LocalDate monthsBack(LocalDate today, int months) {
        return today.getDayOfMonth() == today.lengthOfMonth()
                ? today.withDayOfMonth(1).minusMonths(months - 1)
                : today.minusMonths(months).plusDays(1);
    }
}
