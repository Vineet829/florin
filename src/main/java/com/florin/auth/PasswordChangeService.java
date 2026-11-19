package com.florin.auth;

import com.florin.audit.AuditEventType;
import com.florin.audit.AuditLog;
import com.florin.auth.dto.ChangePasswordRequest;
import com.florin.auth.session.SessionService;
import com.florin.common.AfterCommit;
import com.florin.common.ApiException;
import com.florin.config.AppProperties;
import com.florin.mail.MailService;
import com.florin.ratelimit.RateLimit;
import com.florin.ratelimit.RateLimiter;
import com.florin.user.DemoAccountGuard;
import com.florin.user.User;
import com.florin.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Changes a signed-in account's password. Every other device is signed out;
// this one stays signed in with an access token newer than the change. The
// owner gets an email, in case it wasn't them.
@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final DemoAccountGuard demoAccountGuard;
    private final AuditLog auditLog;
    private final MailService mailService;
    private final AppProperties.Site site;
    private final Clock clock;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            JwtService jwtService,
            RateLimiter rateLimiter,
            DemoAccountGuard demoAccountGuard,
            AuditLog auditLog,
            MailService mailService,
            AppProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.demoAccountGuard = demoAccountGuard;
        this.auditLog = auditLog;
        this.mailService = mailService;
        this.site = properties.site();
        this.clock = clock;
    }

    // Returns an access token for this session that the change doesn't end.
    @Transactional
    public String change(User principal, UUID currentSession, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.getPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
        demoAccountGuard.requireNotDemo(user, "changing the password");
        if (user.getPassword() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "This account has no password yet. Use Forgot password to set one");
        }
        String attempts = "password:" + user.getPkid();
        rateLimiter.requireAvailable(RateLimit.WRONG_PASSWORD, attempts);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            rateLimiter.recordFailure(RateLimit.WRONG_PASSWORD, attempts);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your current password is wrong");
        }
        rateLimiter.reset(RateLimit.WRONG_PASSWORD, attempts);
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The new passwords don't match");
        }
        if (!PasswordPolicy.fits(request.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PasswordPolicy.TOO_LONG);
        }
        if (!PasswordPolicy.isStrong(request.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PasswordPolicy.REQUIREMENTS);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a password different from your current one");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(clock.instant());
        sessionService.endOthers(user, currentSession);
        auditLog.record(AuditEventType.PASSWORD_CHANGED, user);
        String to = user.getEmail();
        String subject = "Your " + site.name() + " password was changed";
        String body = "Hello " + user.getFirstName() + ",\n\n"
                + "The password of your " + site.name() + " account was just changed, and every other"
                + " device signed in to it was signed out.\n\n"
                + "If you didn't change it, reset it now at " + site.clientUrl()
                + "/reset_password_request and check the devices signed in to your account.";
        AfterCommit.run(() -> mailService.send(to, subject, body));
        return jwtService.issueAccessToken(user, currentSession);
    }
}
