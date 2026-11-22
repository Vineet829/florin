package com.florin.report.dto;

import java.math.BigDecimal;

public record PaymentTotals(String currency, long payments, long receipts, BigDecimal received) {}
