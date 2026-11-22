package com.florin.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OverviewComingUp(Recurring recurring, Quotations quotations) {

    public record Recurring(long active, Next90Days next90Days, List<Run> nextRuns) {}

    public record Next90Days(long invoices, BigDecimal amount) {}

    public record Run(
            UUID scheduleId,
            UUID templateId,
            String documentNumber,
            String client,
            BigDecimal amount,
            LocalDate date) {}

    public record Quotations(Awaiting awaiting, Expired expired) {}

    public record Awaiting(long count, BigDecimal amount, List<Quotation> items) {}

    public record Quotation(
            UUID documentId,
            String documentNumber,
            String client,
            BigDecimal amount,
            LocalDate validUntil,
            Long days) {}

    public record Expired(long count, BigDecimal amount, List<ExpiredQuotation> items) {}

    public record ExpiredQuotation(
            UUID documentId, String documentNumber, String client, BigDecimal amount, LocalDate validUntil) {}
}
