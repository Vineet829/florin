package com.florin.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.florin.document.Document;
import com.florin.document.DocumentState;
import com.florin.document.DocumentTotals;
import com.florin.document.DocumentType;
import java.math.BigDecimal;

public record LedgerResponse(
        BigDecimal totalTds,
        BigDecimal totalCredited,
        BigDecimal totalRefunded,
        BigDecimal balanceDue,
        BigDecimal refundDue,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal creditable) {

    public static LedgerResponse from(Document document) {
        boolean credits = document.state() == DocumentState.ISSUED
                && (document.getDocumentType() == DocumentType.INVOICE
                        || document.getDocumentType() == DocumentType.RECEIPT);
        return new LedgerResponse(
                document.getTotalTds(),
                document.getTotalCredited(),
                document.getTotalRefunded(),
                document.getDocumentType() == DocumentType.QUOTATION ? null : DocumentTotals.balanceDue(document),
                DocumentTotals.refundDue(document),
                credits ? document.getTotal().subtract(document.getTotalCredited()).max(BigDecimal.ZERO) : null);
    }
}
