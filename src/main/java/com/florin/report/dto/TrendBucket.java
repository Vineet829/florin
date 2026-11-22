package com.florin.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendBucket(
        LocalDate from,
        LocalDate to,
        BigDecimal billed,
        BigDecimal received,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal moneyIn,
        BigDecimal moneyOut) {}
