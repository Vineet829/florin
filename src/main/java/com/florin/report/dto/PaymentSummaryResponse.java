package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentSummaryResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal total,
        BigDecimal previousTotal,
        long payments,
        List<MethodTotals> methods,
        List<Payer> payers) {

    public record Payer(UUID clientId, String client, long payments, BigDecimal amount) {}
}
