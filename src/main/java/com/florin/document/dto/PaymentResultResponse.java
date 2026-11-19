package com.florin.document.dto;

public record PaymentResultResponse(boolean success, String message, DocumentResponse document) {}
