package com.florin.auth.dto;

public record LoginResponse(
        boolean success,
        String firstName,
        String lastName,
        String username,
        String provider,
        String accessToken) {}
