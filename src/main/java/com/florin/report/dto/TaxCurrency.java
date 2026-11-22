package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record TaxCurrency(
        String currency,
        boolean gst,
        long documents,
        BigDecimal taxable,
        BigDecimal tax,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal reverseChargeTaxable,
        List<TaxTotals> rates,
        List<TaxTotals> months) {}
