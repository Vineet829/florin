package com.florin.report;

import com.florin.common.ApiException;
import com.florin.common.RecentPeriod;
import com.florin.document.PaymentMethod;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.HttpStatus;

public record PaymentSearch(
        String query,
        String method,
        String currency,
        String period,
        LocalDate from,
        LocalDate to,
        String sort,
        boolean ascending,
        int page,
        int size) {

    static final String RECEIPT = "receipt";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("date", "amount", "client");

    public static PaymentSearch of(
            String query,
            String method,
            String currency,
            String period,
            LocalDate from,
            LocalDate to,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        boolean periodic = period != null && !period.isBlank();
        if (periodic && (from != null || to != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period or dates, not both");
        }
        return new PaymentSearch(
                query,
                method == null || method.isBlank() ? null : method(method.trim()),
                currency == null || currency.isBlank() ? null : currency.trim(),
                periodic ? period : null,
                from,
                to,
                sort(sortField),
                "asc".equalsIgnoreCase(sortDirection),
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    static PaymentSearch dated(String currency, LocalDate from, LocalDate to) {
        return new PaymentSearch(null, null, currency, null, from, to, "date", false, 1, 1);
    }

    PaymentSearch on(LocalDate today) {
        if (period == null) {
            return this;
        }
        RecentPeriod days = RecentPeriod.of(period, today);
        return new PaymentSearch(query, method, currency, null, days.from(), days.to(), sort, ascending, page, size);
    }

    private static String method(String value) {
        if (value.equalsIgnoreCase(RECEIPT)) {
            return RECEIPT;
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.getValue().equalsIgnoreCase(value)) {
                return method.getValue();
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown payment method filter");
    }

    private static String sort(String field) {
        String sort = field == null ? "date" : field;
        if (!SORTABLE.contains(sort)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payments can be sorted by date, amount or client");
        }
        return sort;
    }
}
