package com.florin.auth.oauth;

public class OAuthSignInException extends RuntimeException {

    private final String code;

    public OAuthSignInException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
