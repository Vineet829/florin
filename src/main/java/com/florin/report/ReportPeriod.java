package com.florin.report;

import com.florin.common.ApiException;
import com.florin.common.RecentPeriod;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

record ReportPeriod(String key, LocalDate from, LocalDate to) {

    static final String DEFAULT = "12m";
    static final int FIRST_YEAR = 2000;

    static ReportPeriod of(String key, LocalDate today) {
        String value = key == null || key.isBlank() ? DEFAULT : key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "30d", "90d" -> lastDays(value, today);
            case "3m" -> lastMonths(value, 3, today);
            case "6m" -> lastMonths(value, 6, today);
            case "12m" -> lastMonths(value, 12, today);
            case "ytd" -> new ReportPeriod(value, today.withDayOfYear(1), today);
            default -> year(value, today);
        };
    }

    private static ReportPeriod lastDays(String key, LocalDate today) {
        RecentPeriod days = RecentPeriod.of(key, today);
        return new ReportPeriod(key, days.from(), days.to());
    }

    private static ReportPeriod lastMonths(String key, int months, LocalDate today) {
        return new ReportPeriod(key, RecentPeriod.monthsBack(today, months), today);
    }

    private static ReportPeriod year(String key, LocalDate today) {
        if (!key.matches("\\d{4}")) {
            throw unknown();
        }
        int year = Integer.parseInt(key);
        if (year < FIRST_YEAR || year >= today.getYear()) {
            throw unknown();
        }
        return new ReportPeriod(key, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private static ApiException unknown() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Choose a period from the list");
    }

    List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        YearMonth last = YearMonth.from(to);
        for (YearMonth month = YearMonth.from(from); !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    static LocalDate firstDay(YearMonth month, LocalDate from) {
        LocalDate first = month.atDay(1);
        return first.isBefore(from) ? from : first;
    }

    static LocalDate lastDay(YearMonth month, LocalDate to) {
        LocalDate last = month.atEndOfMonth();
        return last.isAfter(to) ? to : last;
    }
}
