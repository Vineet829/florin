package com.florin.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class LongPasswordIntegrationTest extends IntegrationTest {

    private static final String TOO_LONG =
            "Password can be at most 72 characters long, or fewer with letters outside the English alphabet";
    private static final String HINDI = Character.toString(0x0915).repeat(25) + "Aa1!";
    private static final String ENGLISH = "Aa1!" + "a".repeat(69);
    private static final String CURRENT = "Str0ng!pass";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenService verificationTokenService;

    private ResultActions send(String path, String authorization, String json) throws Exception {
        return mockMvc.perform(post(path)
                .with(request -> {
                    request.setRemoteAddr("10.72.0." + (1 + (int) (Math.random() * 250)));
                    return request;
                })
                .headers(authorization == null ? new HttpHeaders() : authorizationHeader(authorization))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static HttpHeaders authorizationHeader(String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        return headers;
    }

    @Test
    void signUp() throws Exception {
        for (String password : new String[] {HINDI, ENGLISH}) {
            String handle = "long" + UUID.randomUUID().toString().substring(0, 8);
            send("/api/v1/auth/register", null, """
                    {"email": "%s@example.test", "username": "%s", "firstName": "Long", "lastName": "Password",
                     "password": "%s", "passwordConfirm": "%s"}
                    """.formatted(handle, handle, password, password))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(TOO_LONG));
        }
    }

    @Test
    void resetAndChange() throws Exception {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(CURRENT));
        userRepository.save(user);

        send("/api/v1/auth/reset_password", null, """
                {"emailToken": "%s", "userId": "%s", "password": "%s", "passwordConfirm": "%s"}
                """.formatted(verificationTokenService.issue(user), user.getId(), HINDI, HINDI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TOO_LONG));
        send("/api/v1/account/password", bearer(user), """
                {"currentPassword": "%s", "newPassword": "%s", "confirmPassword": "%s"}
                """.formatted(CURRENT, HINDI, HINDI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TOO_LONG));
    }
}
