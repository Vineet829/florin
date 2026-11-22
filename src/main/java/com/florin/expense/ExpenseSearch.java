package com.florin.expense;

import com.florin.common.ApiException;
import com.florin.common.RecentPeriod;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

public record ExpenseSearch(
        String query,
        String category,
        String currency,
        String period,
        LocalDate from,
        LocalDate to,
        int page,
        int size) {

    private static final int MAX_PAGE_SIZE = 100;

    public static ExpenseSearch of(
            String query,
            String category,
            String currency,
            String period,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        boolean periodic = period != null && !period.isBlank();
        if (periodic && (from != null || to != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period or dates, not both");
        }
        return new ExpenseSearch(
                query,
                category == null || category.isBlank() ? null : category.trim(),
                currency == null || currency.isBlank() ? null : currency.trim(),
                periodic ? period : null,
                from,
                to,
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    public static ExpenseSearch all() {
        return of(null, null, null, null, null, null, 1, 1);
    }

    ExpenseSearch on(LocalDate today) {
        if (period == null) {
            return this;
        }
        RecentPeriod days = RecentPeriod.of(period, today);
        return new ExpenseSearch(query, category, currency, null, days.from(), days.to(), page, size);
    }
}
