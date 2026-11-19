package com.florin.document.dto;

public record PaymentClaimEnvelope(boolean success, String message, PaymentClaimResponse claim) {}
