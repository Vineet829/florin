package com.florin.document.dto;

import java.util.List;

public record PaymentClaimsResponse(boolean success, List<PaymentClaimResponse> claims) {}
