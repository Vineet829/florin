package com.florin.auth.dto;

public record TwoFactorChallengeResponse(boolean success, boolean twoFactorRequired, String challenge) {}
