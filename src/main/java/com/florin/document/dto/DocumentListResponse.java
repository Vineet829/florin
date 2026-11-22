package com.florin.document.dto;

import java.time.LocalDate;
import java.util.List;

public record DocumentListResponse(
        boolean success,
        long totalDocuments,
        int numberOfPages,
        int page,
        int size,
        LocalDate issuedFrom,
        LocalDate issuedTo,
        List<DocumentResponse> myDocuments,
        List<DocumentListTotals> totals) {}
