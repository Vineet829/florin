package com.florin.auth.dto;

import jakarta.validation.constraints.Size;

public record ConfirmIdentityRequest(
        @Size(max = 128, message = "Your password is wrong") String password,
        @Size(max = 20, message = "That code didn't work") String code) {}
