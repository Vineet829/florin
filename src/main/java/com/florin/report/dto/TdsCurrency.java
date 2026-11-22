package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record TdsCurrency(
        String currency,
        BigDecimal received,
        BigDecimal withheld,
        List<TdsClient> clients,
        List<TdsMonth> months,
        List<TdsPayment> payments) {}
