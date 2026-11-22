package com.florin.auth.twofactor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.common.SecretTokens;
import com.florin.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

@TestPropertySource(properties = "app.encryption.key=")
class TwoFactorWithoutKeyIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    private ResultActions secondStep(String challenge, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login/two-factor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challenge\": \"%s\", \"code\": \"%s\"}".formatted(challenge, code)));
    }

    private String challenge(User user) throws Exception {
        return JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\", \"password\": \"%s\"}"
                                        .formatted(user.getEmail(), PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.challenge");
    }

    @Test
    void recoveryCodesStillWork() throws Exception {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setTwoFactorSecret("sealed-with-a-key-this-server-no-longer-has");
        user.setTwoFactorEnabledAt(Instant.now());
        userRepository.save(user);
        RecoveryCode recovery = new RecoveryCode();
        recovery.setUser(user);
        recovery.setCodeHash(SecretTokens.hash("abcde23456"));
        recoveryCodeRepository.save(recovery);

        secondStep(challenge(user), "123456")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Codes from your authenticator app can't be checked right now. Use a recovery code"));
        secondStep(challenge(user), "abcde-23456").andExpect(status().isOk());
    }
}
