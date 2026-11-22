package com.florin.report.dto;

import java.math.BigDecimal;

public record OverviewTotals(
        Figure billed,
        Figure received,
        Figure profit,
        BigDecimal tax,
        BigDecimal previousTax,
        boolean hasPrevious) {

    public record Figure(BigDecimal current, BigDecimal previous) {}
}
