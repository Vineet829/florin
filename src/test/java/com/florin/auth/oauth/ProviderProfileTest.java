package com.florin.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class ProviderProfileTest {

    private static OAuth2User google(Map<String, Object> claims) {
        return new DefaultOAuth2User(List.of(), claims, claims.containsKey("sub") ? "sub" : "email");
    }

    private static Map<String, Object> claims(Object emailVerified) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "google-1");
        claims.put("email", "neha.apte@gmail.test");
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        return claims;
    }

    @Test
    void fromIdToken() {
        OidcIdToken token = OidcIdToken.withTokenValue("id-token")
                .subject("google-1")
                .claim("email", "neha.apte@gmail.test")
                .claim("email_verified", true)
                .claim("given_name", "Neha")
                .claim("family_name", "Apte")
                .build();

        ProviderProfile profile = ProviderProfile.of("google", new DefaultOidcUser(List.of(), token));

        assertThat(profile).isEqualTo(new ProviderProfile(
                SignInProvider.GOOGLE,
                "google-1",
                "neha.apte@gmail.test",
                true,
                false,
                "Neha",
                "Apte",
                "neha.apte"));
    }

    @Test
    void emailHosted() {
        assertThat(hosted("neha@gmail.com", null)).isTrue();
        assertThat(hosted("Neha@GoogleMail.com", null)).isTrue();
        assertThat(hosted("neha@example.test", "example.test")).isTrue();
        assertThat(hosted("neha@sales.example.test", "example.test")).isTrue();
        assertThat(hosted("neha@example.test", null)).isFalse();
        assertThat(hosted("neha@example.test", " ")).isFalse();
        assertThat(hosted("neha@gmail.com.example.test", null)).isFalse();
        assertThat(hosted("neha@notgmail.com", null)).isFalse();
    }

    @Test
    void hostedDomainFromIdToken() {
        OidcIdToken token = OidcIdToken.withTokenValue("id-token")
                .subject("google-1")
                .claim("email", "neha@example.test")
                .claim("email_verified", true)
                .claim("hd", "example.test")
                .build();

        assertThat(ProviderProfile.of("google", new DefaultOidcUser(List.of(), token)).emailHosted()).isTrue();
    }

    private static boolean hosted(String email, String hostedDomain) {
        Map<String, Object> claims = claims(true);
        claims.put("email", email);
        if (hostedDomain != null) {
            claims.put("hd", hostedDomain);
        }
        return ProviderProfile.of("google", google(claims)).emailHosted();
    }

    @Test
    void emailVerifiedAsText() {
        assertThat(ProviderProfile.of("google", google(claims("true"))).emailVerified()).isTrue();
        assertThat(ProviderProfile.of("google", google(claims("false"))).emailVerified()).isFalse();
        assertThat(ProviderProfile.of("google", google(claims(null))).emailVerified()).isFalse();
    }

    @Test
    void missingSubject() {
        Map<String, Object> claims = claims(true);
        claims.remove("sub");

        assertThatThrownBy(() -> ProviderProfile.of("google", google(claims)))
                .isInstanceOfSatisfying(OAuthSignInException.class,
                        exception -> assertThat(exception.code()).isEqualTo("failed"));
    }
}
