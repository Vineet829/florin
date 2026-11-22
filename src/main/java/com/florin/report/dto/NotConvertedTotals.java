package com.florin.report.dto;

import java.math.BigDecimal;

public record NotConvertedTotals(String currency, long documents, BigDecimal amount) {}
