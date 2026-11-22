package com.florin.auth;

public sealed interface LoginOutcome permits TokenBundle, LoginOutcome.TwoFactorChallenge {

    record TwoFactorChallenge(String challenge) implements LoginOutcome {}
}
