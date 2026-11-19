package com.florin.auth.dto;

public record PasswordChangedResponse(boolean success, String message, String accessToken) {}
