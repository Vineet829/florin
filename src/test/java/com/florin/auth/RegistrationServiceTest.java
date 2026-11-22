package com.florin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.florin.auth.dto.RegisterRequest;
import com.florin.business.BusinessService;
import com.florin.common.ApiException;
import com.florin.config.AppProperties;
import com.florin.mail.MailService;
import com.florin.ratelimit.RateLimiter;
import com.florin.user.User;
import com.florin.user.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationTokenService tokenService;
    @Mock private MailService mailService;
    @Mock private BusinessService businessService;
    @Mock private UnverifiedSignUps unverifiedSignUps;
    @Mock private RateLimiter rateLimiter;

    private RegistrationService service;

    private static final AppProperties PROPERTIES = new AppProperties(
            new AppProperties.Jwt("a", Duration.ofMinutes(10), Duration.ofDays(1)),
            new AppProperties.Cookie("jwt", "/", "None", true),
            new AppProperties.Site("Florin", "http://localhost:3000", "http://localhost:8080", "from@florin.local"),
            new AppProperties.Verification(Duration.ofMinutes(15)),
            new AppProperties.Upload("uploads"));

    @BeforeEach
    void setUp() {
        service = new RegistrationService(
                userRepository, passwordEncoder, tokenService, mailService, businessService, unverifiedSignUps,
                rateLimiter, PROPERTIES);
    }

    private static RegisterRequest request(String password, String confirm) {
        return new RegisterRequest("Neha@Example.com", "neha_user", "Neha", "Apte", password, confirm);
    }

    @Test
    void registersValidUser() {
        when(userRepository.existsByUsername("neha_user")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!pw")).thenReturn("hashed");
        when(tokenService.issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL))).thenReturn("tok");

        String message = service.register(request("Str0ng!pw", "Str0ng!pw"));

        assertThat(message).startsWith("A new user Neha has been registered!");
        verify(userRepository).save(any(User.class));
        verify(businessService).createPersonal(any(User.class));
        verify(tokenService).issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL));
        verify(mailService).send(anyString(), anyString(), anyString());
    }

    @Test
    void lowercasesEmail() {
        when(userRepository.existsByUsername("neha_user")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(tokenService.issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL))).thenReturn("tok");

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        service.register(request("Str0ng!pw", "Str0ng!pw"));
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getEmail()).isEqualTo("neha@example.com");
    }

    @Test
    void rejectsMismatchedPasswords() {
        assertThatThrownBy(() -> service.register(request("Str0ng!pw", "Other1!pw")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Passwords do not match")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> service.register(request("weak", "weak")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least 1 uppercase");
        verify(userRepository, never()).save(any());
    }

    @Test
    void takenEmail() {
        User owner = new User();
        owner.setEmail("neha@example.com");
        owner.setFirstName("Neha");
        owner.setEmailVerified(true);
        when(userRepository.findByEmailIgnoreCase("neha@example.com")).thenReturn(Optional.of(owner));

        String message = service.register(request("Str0ng!pw", "Str0ng!pw"));

        assertThat(message).startsWith("A new user Neha has been registered!");
        verify(passwordEncoder).encode("Str0ng!pw");
        verify(userRepository, never()).save(any());
        verify(tokenService, never()).issue(any(User.class), any(Duration.class));
        verify(mailService).send(eq("neha@example.com"), eq("Someone tried to sign up with your email"), anyString());
    }

    @Test
    void unverifiedEmail() {
        User pending = new User();
        pending.setEmail("neha@example.com");
        when(userRepository.findByEmailIgnoreCase("neha@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.encode("Str0ng!pw")).thenReturn("hashed");
        when(tokenService.issue(any(User.class), eq(RegistrationService.VERIFICATION_LINK_TTL))).thenReturn("tok");

        service.register(request("Str0ng!pw", "Str0ng!pw"));

        verify(unverifiedSignUps).remove(pending);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rejectsBlankEmail() {
        RegisterRequest blank = new RegisterRequest(
                " ", "neha_user", "Neha", "Apte", "Str0ng!pw", "Str0ng!pw");

        assertThatThrownBy(() -> service.register(blank))
                .isInstanceOf(ApiException.class)
                .hasMessage("An email address is required");
    }
}
