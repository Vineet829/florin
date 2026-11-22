package com.florin.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.florin.document.DocumentState;
import com.florin.document.DocumentStatus;
import com.florin.document.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PortalResponse(
        boolean success,
        PublicParty business,
        String client,
        List<Balance> balances,
        List<Entry> documents,
        long total,
        int page,
        int pages) {

    public record Balance(
            String currency,
            BigDecimal invoiced,
            BigDecimal paid,
            BigDecimal credited,
            BigDecimal refunded,
            BigDecimal due,
            BigDecimal overdue) {}

    public record Entry(
            DocumentType documentType,
            String documentNumber,
            DocumentState state,
            DocumentStatus status,
            boolean overdue,
            boolean expired,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant acceptedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant declinedAt,
            LocalDate issueDate,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate dueDate,
            String currency,
            BigDecimal total,
            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal balance) {}
}
