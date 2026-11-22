package com.florin.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ItemSalesDetailResponse(
        boolean success,
        String item,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal quantity,
        BigDecimal amount,
        BigDecimal share,
        long documents,
        List<Client> clients,
        List<Line> lines,
        long linesTotal) {

    public record Client(UUID clientId, String client, BigDecimal quantity, BigDecimal amount) {}

    public record Line(
            UUID documentId,
            String documentNumber,
            String documentType,
            LocalDate issueDate,
            String client,
            BigDecimal quantity,
            BigDecimal amount) {}
}
