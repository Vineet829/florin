package com.florin.expense.dto;

public record ExpenseResultResponse(boolean success, String message, ExpenseResponse expense) {}
