package com.florin.document.dto;

import java.util.List;

public record WaitingClaimsResponse(boolean success, List<WaitingClaimResponse> claims) {}
