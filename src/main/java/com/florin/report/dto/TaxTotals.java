package com.florin.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.YearMonth;

public record TaxTotals(
        @JsonInclude(JsonInclude.Include.NON_NULL) YearMonth month,
        BigDecimal rate,
        long documents,
        BigDecimal taxable,
        BigDecimal tax,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal reverseChargeTaxable) {}
