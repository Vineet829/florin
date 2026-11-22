package com.florin.report.dto;

import java.time.LocalDate;
import java.util.List;

public record PaymentListResponse(
        boolean success,
        long total,
        int numberOfPages,
        int page,
        int size,
        LocalDate from,
        LocalDate to,
        List<PaymentEntry> payments,
        List<PaymentTotals> totals,
        List<String> methods) {}
