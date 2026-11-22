package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal total,
        BigDecimal previousTotal,
        long documents,
        List<TypeTotals> byType,
        Tax tax) {

    public record TypeTotals(String type, long documents, BigDecimal amount) {}

    public record Tax(BigDecimal total, List<Rate> rates) {}

    public record Rate(BigDecimal rate, BigDecimal taxable, BigDecimal tax, long documents) {}
}
