package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record DocumentSummaryResponse(
        boolean success,
        String currency,
        List<String> currencies,
        OverviewOwed outstanding,
        Received receivedLast30Days) {

    public record Received(BigDecimal total, long payments) {}
}
