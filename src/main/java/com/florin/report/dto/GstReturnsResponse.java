package com.florin.report.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record GstReturnsResponse(
        boolean success,
        YearMonth from,
        YearMonth to,
        BigDecimal b2clLimit,
        List<GstFile> files,
        List<Gstr3bRow> gstr3b,
        long documents,
        long skipped,
        long unconverted,
        long missingHsn) {}
