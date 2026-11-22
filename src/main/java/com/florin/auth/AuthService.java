package com.florin.auth;

import com.florin.audit.AuditEventType;
import com.florin.audit.AuditLog;
import com.florin.auth.session.SessionService;
import com.florin.auth.twofactor.TwoFactorService;
import com.florin.common.ApiException;
import com.florin.user.User;
import com.florin.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final TwoFactorService twoFactorService;
    private final AuditLog auditLog;
    private final TransactionTemplate transactions;
    private final String unusedHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SessionService sessionService,
            TwoFactorService twoFactorService,
            AuditLog auditLog,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.twoFactorService = twoFactorService;
        this.auditLog = auditLog;
        this.transactions = new TransactionTemplate(transactionManager);
        this.unusedHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginOutcome login(String email, String password, String presentedRefreshToken) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please provide an email and password");
        }

        Optional<User> account = userRepository.findByEmailIgnoreCase(email.trim());
        String hash = account.map(User::getPassword).orElse(null);
        boolean matches = passwordEncoder.matches(password, hash == null ? unusedHash : hash);
        if (hash == null || !matches) {
            auditLog.signInFailed(
                    email, account.orElse(null), account.isEmpty() ? "No account with this email" : "Wrong password");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }
        return transactions.execute(status -> signIn(email, account.get().getPkid(), hash, presentedRefreshToken));
    }

    private LoginOutcome signIn(String email, Long pkid, String checkedHash, String presentedRefreshToken) {
        User user = userRepository
                .findById(pkid)
                .filter(found -> checkedHash.equals(found.getPassword()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password"));

        if (!user.isEmailVerified()) {
            auditLog.signInFailed(email, user, "Email not verified");
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You are not verified. Check your email, a verification email link was"
                            + " sent when you registered");
        }
        if (!user.isActive()) {
            auditLog.signInFailed(email, user, "Account deactivated");
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You have been deactivated by the admin and login is impossible. Contact us"
                            + " for enquiries");
        }

        if (user.isTwoFactorEnabled()) {
            return new LoginOutcome.TwoFactorChallenge(jwtService.issueTwoFactorChallenge(user, null));
        }
        return startSession(user, presentedRefreshToken, null);
    }

    @Transactional
    public TokenBundle completeTwoFactor(String challenge, String code, String presentedRefreshToken) {
        Optional<JwtService.Challenge> read = jwtService.readTwoFactorChallenge(challenge);
        User user = read.map(JwtService.Challenge::user)
                .flatMap(userRepository::findForUpdateByExternalId)
                .filter(candidate -> candidate.isActive() && candidate.isTwoFactorEnabled())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Your sign-in timed out. Log in again"));
        Optional<TwoFactorService.Method> method = twoFactorService.verify(user, code);
        if (method.isEmpty()) {
            auditLog.signInFailed(user.getEmail(), user, "Wrong two-factor code");
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code didn't work");
        }
        return startSession(user, presentedRefreshToken, detail(read.get().via(), method.get().description()));
    }

    private static String detail(String via, String method) {
        return via == null ? method : via + " and " + method.replaceFirst("^With ", "");
    }

    public TokenBundle startSession(User user, String presentedRefreshToken, String detail) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            sessionService.end(presentedRefreshToken);
        }
        SessionService.Continued started = sessionService.start(user);
        UUID sessionId = started.session().getId();
        auditLog.record(AuditEventType.SIGN_IN, user, null, null, detail, sessionId);
        return new TokenBundle(jwtService.issueAccessToken(user, sessionId), started.refreshToken(), user);
    }

    @Transactional
    public Optional<TokenBundle> refresh(String presentedRefreshToken) {
        return sessionService.refresh(presentedRefreshToken).map(continued -> {
            User user = continued.session().getUser();
            return new TokenBundle(
                    jwtService.issueAccessToken(user, continued.session().getId()), continued.refreshToken(), user);
        });
    }

    @Transactional
    public Optional<String> logout(String presentedRefreshToken) {
        return sessionService.end(presentedRefreshToken).map(session -> {
            User user = session.getUser();
            auditLog.record(AuditEventType.SIGN_OUT, user, null, null, null, session.getId());
            return user.getFirstName() + ",you have been logged out successfully";
        });
    }
}
