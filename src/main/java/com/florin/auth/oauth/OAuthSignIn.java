package com.florin.auth.oauth;

import com.florin.audit.AuditLog;
import com.florin.auth.AuthService;
import com.florin.auth.JwtService;
import com.florin.auth.LoginOutcome;
import com.florin.user.DemoAccountGuard;
import com.florin.user.User;
import com.florin.user.UserIdentity;
import com.florin.user.UserIdentityRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OAuthSignIn {

    private final OAuthAccounts accounts;
    private final UserIdentityRepository identities;
    private final AuthService authService;
    private final JwtService jwtService;
    private final DemoAccountGuard demoAccountGuard;
    private final AuditLog auditLog;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OAuthSignIn(
            OAuthAccounts accounts,
            UserIdentityRepository identities,
            AuthService authService,
            JwtService jwtService,
            DemoAccountGuard demoAccountGuard,
            AuditLog auditLog,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.identities = identities;
        this.authService = authService;
        this.jwtService = jwtService;
        this.demoAccountGuard = demoAccountGuard;
        this.auditLog = auditLog;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public LoginOutcome signIn(ProviderProfile profile, String presentedRefreshToken) {
        String via = "With " + profile.provider().label();
        Long identityKey = accounts.identityFor(profile, via);
        return transaction.execute(status -> {
            UserIdentity identity = identities.findById(identityKey).orElseThrow();
            User user = identity.getUser();
            if (!user.isActive()) {
                auditLog.signInFailed(user.getEmail(), user, via + ": account deactivated");
                throw new OAuthSignInException("deactivated");
            }
            if (demoAccountGuard.isDemo(user)) {
                auditLog.signInFailed(user.getEmail(), user, via + ": demo account");
                throw new OAuthSignInException("demo_account");
            }
            identity.setLastUsedAt(clock.instant());
            if (user.isTwoFactorEnabled()) {
                return new LoginOutcome.TwoFactorChallenge(jwtService.issueTwoFactorChallenge(user, via));
            }
            return authService.startSession(user, presentedRefreshToken, via);
        });
    }
}
