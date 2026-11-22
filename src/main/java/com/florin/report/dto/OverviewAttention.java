package com.florin.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OverviewAttention(List<Item> items, Counts counts) {

    public record Item(
            String kind,
            UUID documentId,
            String documentNumber,
            String client,
            UUID clientId,
            BigDecimal amount,
            LocalDate date,
            Long days) {}

    public record Counts(long paymentReports, long overdue, long dueSoon, long drafts) {}
}
