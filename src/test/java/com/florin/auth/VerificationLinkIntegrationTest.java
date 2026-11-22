package com.florin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.config.AppProperties;
import com.florin.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class VerificationLinkIntegrationTest extends IntegrationTest {

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private AppProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void opens(String token, Object userId, String page) throws Exception {
        mockMvc.perform(get("/api/v1/auth/verify/{token}/{user}", token, userId))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", properties.site().clientUrl() + page));
    }

    @Test
    void linkShowsMatchingPage() throws Exception {
        User user = createUser();
        user.setEmailVerified(false);
        userRepository.save(user);
        String token = tokenService.issue(user);

        opens("not-the-token", user.getId(), "/auth/verify?expired=1");
        opens(token, UUID.randomUUID(), "/auth/verify?expired=1");
        opens(token, user.getId(), "/auth/verify");
        opens(token, user.getId(), "/auth/verify?already=1");
    }

    @Test
    void lastsADay() throws Exception {
        String handle = "link" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(request -> {
                            request.setRemoteAddr("10.24.0." + (1 + (int) (Math.random() * 250)));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s@example.test", "username": "%s", "firstName": "Link",
                                 "lastName": "Test", "password": "Str0ng!pw", "passwordConfirm": "Str0ng!pw"}
                                """.formatted(handle, handle)))
                .andExpect(status().isOk());

        Map<String, Object> token = jdbcTemplate.queryForMap(
                "select t.created_at, t.expires_at from verify_reset_tokens t join users u on u.pkid = t.user_pkid"
                        + " where u.email = ?",
                handle + "@example.test");
        Duration lasts = Duration.between(
                ((java.sql.Timestamp) token.get("created_at")).toInstant(),
                ((java.sql.Timestamp) token.get("expires_at")).toInstant());
        assertThat(lasts).isEqualTo(Duration.ofHours(24));
        assertThat(Instant.now()).isBefore(((java.sql.Timestamp) token.get("expires_at")).toInstant());
    }
}
