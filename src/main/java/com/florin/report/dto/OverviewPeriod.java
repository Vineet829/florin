package com.florin.report.dto;

import java.time.LocalDate;

public record OverviewPeriod(
        String key, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, String step) {}
