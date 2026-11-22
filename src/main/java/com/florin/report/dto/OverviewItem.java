package com.florin.report.dto;

import java.math.BigDecimal;

public record OverviewItem(String item, BigDecimal quantity, BigDecimal amount, long documents) {}
