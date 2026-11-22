package com.florin.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class SessionIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!pass";

    private record SignedIn(String access, String refresh) {}

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User withPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private static SignedIn read(MockHttpServletResponse response) throws Exception {
        Cookie cookie = response.getCookie("jwt");
        return new SignedIn(
                "Bearer " + JsonPath.read(response.getContentAsString(), "$.accessToken"),
                cookie == null ? null : cookie.getValue());
    }

    private SignedIn signIn(User user) throws Exception {
        return read(mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/129.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/new_access_token").cookie(new Cookie("jwt", token)));
    }

    private ResultActions profile(String access) throws Exception {
        return mockMvc.perform(get("/api/v1/user/profile").header(HttpHeaders.AUTHORIZATION, access));
    }

    private List<UserSession> active(User user) {
        return sessionRepository.findActive(user.getPkid(), Instant.now());
    }

    @Test
    void signInAndRefresh() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        profile(first.access()).andExpect(status().isOk());
        assertThat(active(user)).singleElement().satisfies(session -> {
            assertThat(session.getUserAgent()).isEqualTo("Mozilla/5.0 (Macintosh) Chrome/129.0");
            assertThat(session.getIp()).isEqualTo("127.0.0.1");
        });

        SignedIn next = read(refresh(first.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        assertThat(next.refresh()).isNotNull().isNotEqualTo(first.refresh());
        profile(next.access()).andExpect(status().isOk());
        assertThat(active(user)).hasSize(1);
    }

    @Test
    void concurrentRefresh() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andReturn().getResponse());

        MockHttpServletResponse late = refresh(first.refresh())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn()
                .getResponse();

        profile(read(late).access()).andExpect(status().isOk());
        refresh(rotated.refresh()).andExpect(status().isOk());
    }

    @Test
    void noSecondSwapWithinGrace() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        refresh(rotated.refresh())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        MockHttpServletResponse late = refresh(first.refresh())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn()
                .getResponse();

        profile(read(late).access()).andExpect(status().isOk());
        assertThat(active(user)).hasSize(1);
    }

    @Test
    void swapsAgainAfterGrace() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andReturn().getResponse());
        jdbcTemplate.update(
                "update user_sessions set rotated_at = now() - interval '1 minute' where user_pkid = ?",
                user.getPkid());

        SignedIn next = read(refresh(rotated.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        assertThat(next.refresh()).isNotNull().isNotEqualTo(rotated.refresh());
        refresh(next.refresh()).andExpect(status().isOk());
    }

    @Test
    void reusedToken() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn other = signIn(user);
        refresh(first.refresh()).andExpect(status().isOk());
        jdbcTemplate.update(
                "update user_sessions set rotated_at = now() - interval '1 minute' where user_pkid = ?",
                user.getPkid());

        refresh(first.refresh()).andExpect(status().isForbidden());

        assertThat(active(user)).isEmpty();
        profile(other.access()).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForList(
                        "select type from audit_events where user_pkid = ? order by pkid",
                        String.class,
                        user.getPkid()))
                .endsWith("SESSION_TOKEN_REUSED");
    }

    @Test
    void reusedTokenOnTheDemo() throws Exception {
        User demo = demoUser();
        demo.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(demo);
        SignedIn first = signIn(demo);
        SignedIn otherVisitor = signIn(demo);
        refresh(first.refresh()).andExpect(status().isOk());
        jdbcTemplate.update(
                "update user_sessions set rotated_at = now() - interval '1 minute' where user_pkid = ?",
                demo.getPkid());

        refresh(first.refresh()).andExpect(status().isForbidden());

        profile(otherVisitor.access()).andExpect(status().isOk());
        refresh(otherVisitor.refresh()).andExpect(status().isOk());
    }

    @Test
    void signOut() throws Exception {
        User user = withPassword();
        SignedIn session = signIn(user);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("jwt", session.refresh())))
                .andExpect(status().isOk());

        profile(session.access()).andExpect(status().isForbidden());
        refresh(session.refresh()).andExpect(status().isForbidden());
    }

    @Test
    void signOutWithReplacedCookie() throws Exception {
        User user = withPassword();
        SignedIn first = signIn(user);
        SignedIn rotated = read(refresh(first.refresh()).andExpect(status().isOk()).andReturn().getResponse());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("jwt", first.refresh())))
                .andExpect(status().isOk());

        profile(rotated.access()).andExpect(status().isForbidden());
        refresh(rotated.refresh()).andExpect(status().isForbidden());
        assertThat(active(user)).isEmpty();
    }

    @Test
    void signOutTakesPost() throws Exception {
        User user = withPassword();
        SignedIn session = signIn(user);

        mockMvc.perform(get("/api/v1/auth/logout").cookie(new Cookie("jwt", session.refresh())))
                .andExpect(status().isMethodNotAllowed());

        profile(session.access()).andExpect(status().isOk());
    }

    @Test
    void refreshCookieStaysOnSite() throws Exception {
        User user = withPassword();

        String cookie = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.SET_COOKIE);

        assertThat(cookie).startsWith("jwt=").contains("SameSite=Lax");
    }

    @Test
    void expiredSessionCannotRefresh() throws Exception {
        User user = withPassword();
        SignedIn session = signIn(user);
        jdbcTemplate.update(
                "update user_sessions set expires_at = now() - interval '1 second' where user_pkid = ?",
                user.getPkid());

        refresh(session.refresh()).andExpect(status().isForbidden());
        profile(session.access()).andExpect(status().isForbidden());
    }
}
