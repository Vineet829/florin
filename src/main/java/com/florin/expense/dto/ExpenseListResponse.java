package com.florin.expense.dto;

import java.time.LocalDate;
import java.util.List;

public record ExpenseListResponse(
        boolean success,
        long totalExpenses,
        int numberOfPages,
        LocalDate from,
        LocalDate to,
        List<ExpenseResponse> expenses,
        List<ExpenseListTotals> totals) {}
