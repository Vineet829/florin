package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReceivablesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        BigDecimal total,
        long invoices,
        Group current,
        Group overdue) {

    public record Group(BigDecimal amount, long invoices, List<OverviewOwed.Band> bands, List<Client> clients) {}

    public record Client(UUID clientId, String client, BigDecimal amount, long invoices) {}
}
