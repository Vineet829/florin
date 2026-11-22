package com.florin.document.dto;

import com.florin.document.PaymentSection;
import com.florin.payment.UpiPayment;
import java.math.BigDecimal;

public record PublicPayment(
        String accountHolder,
        String bankName,
        String accountNumber,
        String bankCodeLabel,
        String bankCode,
        String upiId,
        String payLink,
        String instructions,
        PublicUpi upi) {

    public record PublicUpi(String uri, BigDecimal amount, boolean amountIncluded) {

        static PublicUpi from(UpiPayment upi) {
            return upi == null ? null : new PublicUpi(upi.uri(), upi.amount(), upi.amountIncluded());
        }
    }

    public static PublicPayment from(PaymentSection section) {
        return new PublicPayment(
                section.accountHolder(),
                section.bankName(),
                section.accountNumber(),
                section.bankCodeName(),
                section.bankCode(),
                section.upiId(),
                section.link(),
                section.instructions(),
                PublicUpi.from(section.upi()));
    }
}
