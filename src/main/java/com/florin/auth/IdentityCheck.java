package com.florin.auth;

import com.florin.auth.dto.ConfirmIdentityRequest;
import com.florin.auth.twofactor.TwoFactorService;
import com.florin.common.ApiException;
import com.florin.ratelimit.RateLimit;
import com.florin.ratelimit.RateLimiter;
import com.florin.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class IdentityCheck {

    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final TwoFactorService twoFactorService;

    public IdentityCheck(PasswordEncoder passwordEncoder, RateLimiter rateLimiter, TwoFactorService twoFactorService) {
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.twoFactorService = twoFactorService;
    }

    public void require(User user, ConfirmIdentityRequest request) {
        if (user.getPassword() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Set a password first, with Forgot password on the sign-in page");
        }
        String password = request == null ? null : request.password();
        if (password == null || password.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your password to confirm");
        }
        String attempts = "password:" + user.getPkid();
        rateLimiter.requireAvailable(RateLimit.WRONG_PASSWORD, attempts);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            rateLimiter.recordFailure(RateLimit.WRONG_PASSWORD, attempts);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your password is wrong");
        }
        rateLimiter.reset(RateLimit.WRONG_PASSWORD, attempts);
        if (user.isTwoFactorEnabled() && twoFactorService.verify(user, request.code()).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code didn't work");
        }
    }
}
