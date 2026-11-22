package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record OverviewOwed(
        BigDecimal total,
        BigDecimal notDue,
        BigDecimal overdue,
        long invoices,
        long overdueInvoices,
        List<Band> bands) {

    public record Band(String key, BigDecimal amount, long invoices) {}
}
