package com.florin.customer.dto;

public record ImportResultResponse(boolean success, String message, int added, int skipped) {}
