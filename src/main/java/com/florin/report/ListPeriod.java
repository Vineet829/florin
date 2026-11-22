package com.florin.report;

import com.florin.common.ApiException;
import com.florin.report.dto.ViewPeriod;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.IsoFields;
import java.util.Locale;
import org.springframework.http.HttpStatus;

record ListPeriod(String key, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {

    private static final LocalDate FIRST_DAY = LocalDate.of(1, 1, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(9999, 12, 31);

    static ListPeriod of(String key, LocalDate today) {
        String value = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "" -> new ListPeriod(value, null, null, null, null);
            case "30d", "90d", "12m", "ytd" -> {
                OverviewWindow window = OverviewWindow.of(value, today);
                yield new ListPeriod(value, window.from(), window.to(), window.previousFrom(), window.previousTo());
            }
            case "this-month" -> running(value, today.withDayOfMonth(1), Period.ofMonths(1), today);
            case "last-month" -> past(value, today.withDayOfMonth(1).minusMonths(1), Period.ofMonths(1));
            case "this-quarter" -> running(value, today.with(IsoFields.DAY_OF_QUARTER, 1), Period.ofMonths(3), today);
            case "this-year" -> running(value, today.withDayOfYear(1), Period.ofYears(1), today);
            case "last-year" -> past(value, today.withDayOfYear(1).minusYears(1), Period.ofYears(1));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period from the list");
        };
    }

    private static ListPeriod running(String key, LocalDate first, Period length, LocalDate today) {
        return new ListPeriod(key, first, today, first.minus(length), today.minus(length));
    }

    private static ListPeriod past(String key, LocalDate first, Period length) {
        return new ListPeriod(key, first, first.plus(length).minusDays(1), first.minus(length), first.minusDays(1));
    }

    boolean compared() {
        return previousFrom != null;
    }

    LocalDate firstDay() {
        return from == null ? FIRST_DAY : from;
    }

    LocalDate lastDay() {
        return to == null ? LAST_DAY : to;
    }

    ViewPeriod view() {
        return new ViewPeriod(key, from, to, previousFrom, previousTo);
    }
}
