package com.florin.settings.dto;

public record PaymentDetailsEnvelope(boolean success, String message, PaymentDetailsResponse paymentDetails) {}
