package com.florin.auth.twofactor.dto;

public record TwoFactorSetupResponse(boolean success, String secret, String otpauthUri) {}
