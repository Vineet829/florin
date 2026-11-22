package com.florin.document.dto;

import com.florin.document.TaxSummary;
import java.math.BigDecimal;

public record TaxResponse(
        BigDecimal rate, BigDecimal taxable, BigDecimal tax, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {

    public static TaxResponse from(TaxSummary summary) {
        return new TaxResponse(
                BillingItemResponse.plain(summary.getRate()),
                summary.getTaxable(),
                summary.getTax(),
                summary.getCgst(),
                summary.getSgst(),
                summary.getIgst());
    }
}
