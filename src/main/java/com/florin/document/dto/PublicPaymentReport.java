package com.florin.document.dto;

import com.florin.document.PaymentClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PublicPaymentReport(
        BigDecimal amount, LocalDate paidOn, String method, PaymentClaim.Status status, Instant reportedAt) {

    public static PublicPaymentReport from(PaymentClaim claim) {
        return new PublicPaymentReport(
                claim.getAmount(),
                claim.getPaidOn(),
                claim.getMethod().label(),
                claim.getStatus(),
                claim.getCreatedAt());
    }
}
