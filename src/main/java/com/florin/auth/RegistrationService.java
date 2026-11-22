package com.florin.auth;

import com.florin.auth.dto.RegisterRequest;
import com.florin.business.BusinessService;
import com.florin.common.AfterCommit;
import com.florin.common.ApiException;
import com.florin.config.AppProperties;
import com.florin.mail.MailService;
import com.florin.ratelimit.RateLimit;
import com.florin.ratelimit.RateLimiter;
import com.florin.user.User;
import com.florin.user.UserRepository;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    static final Duration VERIFICATION_LINK_TTL = Duration.ofHours(24);

    public static final String VERIFICATION_RESENT =
            "If that email has an account waiting to be verified, we've sent it a new link. It works for 24 hours";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final MailService mailService;
    private final BusinessService businessService;
    private final UnverifiedSignUps unverifiedSignUps;
    private final RateLimiter rateLimiter;
    private final AppProperties.Site site;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            VerificationTokenService tokenService,
            MailService mailService,
            BusinessService businessService,
            UnverifiedSignUps unverifiedSignUps,
            RateLimiter rateLimiter,
            AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mailService = mailService;
        this.businessService = businessService;
        this.unverifiedSignUps = unverifiedSignUps;
        this.rateLimiter = rateLimiter;
        this.site = properties.site();
    }

    @Transactional
    public String register(RegisterRequest request) {
        requireText(request.email(), "An email address is required");
        requireText(request.username(), "A username is required");
        if (isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw badRequest("You must enter a full name with a first and last name");
        }
        requireText(request.password(), "You must enter a password");
        requireText(request.passwordConfirm(), "Confirm password field is required");
        if (!request.password().equals(request.passwordConfirm())) {
            throw badRequest("Passwords do not match");
        }
        if (!PasswordPolicy.fits(request.password())) {
            throw badRequest(PasswordPolicy.TOO_LONG);
        }
        if (!PasswordPolicy.isStrong(request.password())) {
            throw badRequest(PasswordPolicy.REQUIREMENTS);
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();
        Optional<User> holder = userRepository.findByEmailIgnoreCase(email);
        if (holder.isPresent() && !holder.get().isEmailVerified()) {
            unverifiedSignUps.remove(holder.get());
            holder = Optional.empty();
        }
        if (userRepository.existsByUsername(username)) {
            throw badRequest("That username is already taken");
        }
        rateLimiter.consume(RateLimit.ACCOUNT_EMAIL_TO, email);
        String password = passwordEncoder.encode(request.password());
        if (holder.isPresent()) {
            tellOwnerOfSignUp(holder.get());
            return registered(request.firstName().trim());
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPassword(password);
        userRepository.save(user);
        businessService.createPersonal(user);

        sendVerification(user);
        return registered(user.getFirstName());
    }

    private static String registered(String firstName) {
        return "A new user "
                + firstName
                + " has been registered! A Verification email has been sent to your account. Please"
                + " verify within 24 hours";
    }

    private void tellOwnerOfSignUp(User owner) {
        String to = owner.getEmail();
        String body = "Hello "
                + owner.getFirstName()
                + ", someone tried to sign up for "
                + site.name()
                + " with this email address, which already has an account. If it was you, log in at "
                + site.clientUrl()
                + "/login, or reset your password there if you have forgotten it. If it wasn't, you can"
                + " ignore this email.";
        AfterCommit.run(() -> mailService.send(to, "Someone tried to sign up with your email", body));
    }

    @Transactional
    public void verify(String userId, String token) {
        User user = parseId(userId)
                .flatMap(userRepository::findByExternalId)
                .orElseThrow(() -> badRequest("We were unable to find a user for this token"));
        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "This user has already been verified. Please login");
        }

        VerifyResetToken verificationToken = tokenService
                .findUsable(token)
                .filter(entity -> entity.getUser().getPkid().equals(user.getPkid()))
                .orElseThrow(() -> badRequest("Token invalid! Your token may have expired"));

        user.setEmailVerified(true);
        userRepository.save(user);
        tokenService.consume(verificationToken);

        String to = user.getEmail();
        String body = "Hello "
                + user.getFirstName()
                + ", your "
                + site.name()
                + " account is now verified. You can log in at "
                + site.clientUrl()
                + "/login";
        AfterCommit.run(() -> mailService.send(to, "Welcome - Account Verified", body));
    }

    @Transactional
    public String resend(String email) {
        if (isBlank(email)) {
            throw badRequest("An email must be provided");
        }
        userRepository
                .findByEmailIgnoreCase(email.trim())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerification);
        return VERIFICATION_RESENT;
    }

    private void sendVerification(User user) {
        String token = tokenService.issue(user, VERIFICATION_LINK_TTL);
        String link = site.serverUrl() + "/api/v1/auth/verify/" + token + "/" + user.getId();
        String to = user.getEmail();
        String body = "Hello "
                + user.getFirstName()
                + ", welcome to "
                + site.name()
                + ". Please verify your account within 24 hours using this link: "
                + link;
        AfterCommit.run(() -> mailService.send(to, "Account Verification", body));
    }

    private static Optional<UUID> parseId(String userId) {
        try {
            return Optional.of(UUID.fromString(userId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static void requireText(String value, String message) {
        if (isBlank(value)) {
            throw badRequest(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
