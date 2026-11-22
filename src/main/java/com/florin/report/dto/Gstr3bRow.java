package com.florin.report.dto;

import java.math.BigDecimal;

public record Gstr3bRow(
        String table, String description, BigDecimal taxable, BigDecimal igst, BigDecimal cgst, BigDecimal sgst) {}
