package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerSalesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        long totalClients,
        int page,
        int size,
        int numberOfPages,
        Totals totals,
        List<Client> clients) {

    public record Totals(long documents, BigDecimal billed, BigDecimal paid, BigDecimal owed) {}

    public record Client(
            UUID clientId, String client, long documents, BigDecimal billed, BigDecimal paid, BigDecimal owed) {}
}
