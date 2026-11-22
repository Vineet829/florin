package com.florin.auth;

import com.florin.user.User;

public record TokenBundle(String accessToken, String refreshToken, User user) implements LoginOutcome {}
