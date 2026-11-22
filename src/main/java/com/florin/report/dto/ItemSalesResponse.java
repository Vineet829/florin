package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record ItemSalesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal total,
        long totalItems,
        int page,
        int size,
        int numberOfPages,
        List<Item> items) {

    public record Item(String item, BigDecimal quantity, BigDecimal amount, BigDecimal share, long documents) {}
}
