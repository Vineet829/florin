package com.florin.auth;

import com.florin.user.User;

// The refresh token is null when the session kept the one the browser has.
public record TokenBundle(String accessToken, String refreshToken, User user) implements LoginOutcome {}
