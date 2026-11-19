package com.florin.expense.dto;

import java.util.List;

public record ExpenseCategoriesResponse(boolean success, List<String> categories) {}
