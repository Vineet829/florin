package com.florin.document.dto;

import java.math.BigDecimal;

public record DocumentListTotals(
        String currency,
        long documents,
        BigDecimal billed,
        BigDecimal balanceDue,
        BigDecimal overdue,
        long overdueDocuments) {}
