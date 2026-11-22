package com.florin.auth;

import com.florin.auth.dto.EmailRequest;
import com.florin.auth.dto.MessageResponse;
import com.florin.auth.dto.RegisterRequest;
import com.florin.auth.dto.ResetPasswordRequest;
import com.florin.common.ApiException;
import com.florin.config.AppProperties;
import com.florin.ratelimit.RateLimit;
import com.florin.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;
    private final String clientUrl;

    public RegistrationController(
            RegistrationService registrationService,
            PasswordResetService passwordResetService,
            RateLimiter rateLimiter,
            AppProperties properties) {
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
        this.clientUrl = properties.site().clientUrl();
    }

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.SIGN_UP, http.getRemoteAddr());
        return new MessageResponse(true, registrationService.register(request));
    }

    @GetMapping("/verify/{emailToken}/{userId}")
    public ResponseEntity<Void> verify(
            @PathVariable String emailToken, @PathVariable String userId) {
        String page = clientUrl + "/auth/verify";
        try {
            registrationService.verify(userId, emailToken);
        } catch (ApiException failed) {
            page += failed.getStatus() == HttpStatus.CONFLICT ? "?already=1" : "?expired=1";
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(page)).build();
    }

    @PostMapping("/resend_email_token")
    public MessageResponse resend(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.VERIFICATION_EMAIL, http.getRemoteAddr());
        limitEmailsTo(request.email());
        return new MessageResponse(true, registrationService.resend(request.email()));
    }

    @PostMapping("/reset_password_request")
    public MessageResponse resetPasswordRequest(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.PASSWORD_RESET, http.getRemoteAddr());
        limitEmailsTo(request.email());
        return new MessageResponse(true, passwordResetService.requestReset(request.email()));
    }

    private void limitEmailsTo(String email) {
        if (email != null && !email.isBlank()) {
            rateLimiter.consume(RateLimit.ACCOUNT_EMAIL_TO, email.trim().toLowerCase(Locale.ROOT));
        }
    }

    @PostMapping("/reset_password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return new MessageResponse(true, passwordResetService.resetPassword(request));
    }
}
