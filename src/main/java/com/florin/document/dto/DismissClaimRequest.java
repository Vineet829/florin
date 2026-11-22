package com.florin.document.dto;

import jakarta.validation.constraints.Size;

public record DismissClaimRequest(
        @Size(max = 200, message = "The reason can be at most 200 characters") String reason) {}
