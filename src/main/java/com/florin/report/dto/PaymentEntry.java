package com.florin.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentEntry(
        String kind,
        UUID id,
        LocalDate date,
        String client,
        Document document,
        String method,
        String note,
        BigDecimal amount,
        String currency) {

    public record Document(UUID id, String number, String type) {}
}
