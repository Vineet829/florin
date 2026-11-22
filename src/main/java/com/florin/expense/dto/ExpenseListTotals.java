package com.florin.expense.dto;

import java.math.BigDecimal;

public record ExpenseListTotals(String currency, long expenses, BigDecimal spent) {}
