package com.florin.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OverviewClient(UUID clientId, String client, BigDecimal billed, BigDecimal owed, long documents) {}
