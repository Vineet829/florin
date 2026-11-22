package com.florin.document.dto;

import com.florin.document.CustomerSnapshot;
import com.florin.document.PaymentClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WaitingClaimResponse(
        UUID id,
        UUID documentId,
        String documentNumber,
        String client,
        BigDecimal amount,
        String currency,
        LocalDate paidOn,
        String methodLabel,
        Instant reportedAt) {

    public static WaitingClaimResponse from(PaymentClaim claim) {
        CustomerSnapshot client = claim.getDocument().getCustomerSnapshot();
        return new WaitingClaimResponse(
                claim.getId(),
                claim.getDocument().getId(),
                claim.getDocument().getDocumentNumber(),
                client == null ? null : client.getName(),
                claim.getAmount(),
                claim.getCurrency(),
                claim.getPaidOn(),
                claim.getMethod().label(),
                claim.getCreatedAt());
    }
}
