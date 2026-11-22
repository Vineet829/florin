package com.florin.report.dto;

import java.time.LocalDate;

public record ViewPeriod(String key, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {}
