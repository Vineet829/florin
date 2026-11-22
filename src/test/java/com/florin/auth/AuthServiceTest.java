package com.florin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.florin.audit.AuditLog;
import com.florin.auth.session.SessionService;
import com.florin.auth.twofactor.TwoFactorService;
import com.florin.common.ApiException;
import com.florin.user.User;
import com.florin.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "neha@example.test";
    private static final String HASH = "hash-of-the-password";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private SessionService sessionService;
    @Mock private TwoFactorService twoFactorService;
    @Mock private AuditLog auditLog;
    @Mock private PlatformTransactionManager transactionManager;

    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash-nobody-matches");
        service = new AuthService(
                userRepository, passwordEncoder, jwtService, sessionService, twoFactorService, auditLog,
                transactionManager);
        user = new User();
        user.setPkid(7L);
        user.setEmail(EMAIL);
        user.setPassword(HASH);
        user.setEmailVerified(true);
        user.setTwoFactorEnabledAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void checksThePasswordFirst() {
        when(passwordEncoder.matches("Str0ng!pass", HASH)).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.issueTwoFactorChallenge(user, null)).thenReturn("challenge");

        LoginOutcome outcome = service.login(EMAIL, "Str0ng!pass", null);

        assertThat(outcome).isEqualTo(new LoginOutcome.TwoFactorChallenge("challenge"));
        InOrder order = inOrder(passwordEncoder, transactionManager, userRepository);
        order.verify(passwordEncoder).matches("Str0ng!pass", HASH);
        order.verify(transactionManager).getTransaction(any());
        order.verify(userRepository).findById(7L);
        order.verify(transactionManager).commit(any());
    }

    @Test
    void wrongPassword() {
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "wrong", null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(auditLog).signInFailed(EMAIL, user, "Wrong password");
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void passwordChangedMeanwhile() {
        when(passwordEncoder.matches("Str0ng!pass", HASH)).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        User changed = new User();
        changed.setPkid(7L);
        changed.setPassword("hash-of-a-new-password");
        when(userRepository.findById(7L)).thenReturn(Optional.of(changed));

        assertThatThrownBy(() -> service.login(EMAIL, "Str0ng!pass", null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Incorrect email or password");
        verify(transactionManager).rollback(any());
    }
}
