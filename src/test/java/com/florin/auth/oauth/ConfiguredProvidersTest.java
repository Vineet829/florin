package com.florin.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.config.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class ConfiguredProvidersTest {

    private static ConfiguredProviders with(OAuthProperties.Provider google) {
        return new ConfiguredProviders(new OAuthProperties("https://app.example/", google));
    }

    @Test
    void configuredGoogleUsesItsUrls() {
        ConfiguredProviders providers = with(new OAuthProperties.Provider(" id ", " secret ", null));

        assertThat(providers.configured()).containsExactly(SignInProvider.GOOGLE);
        ClientRegistration google = providers.findByRegistrationId("google");
        assertThat(google.getClientId()).isEqualTo("id");
        assertThat(google.getClientSecret()).isEqualTo("secret");
        assertThat(google.getRedirectUri())
                .isEqualTo("https://app.example/api/v1/auth/oauth2/callback/{registrationId}");
        assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(google.getProviderDetails().getAuthorizationUri()).startsWith("https://accounts.google.com/");
        assertThat(providers).hasSize(1);
    }

    @Test
    void notConfigured() {
        for (ConfiguredProviders providers : new ConfiguredProviders[] {
            with(new OAuthProperties.Provider("id", " ", null)),
            with(new OAuthProperties.Provider(null, "secret", null)),
            with(null)
        }) {
            assertThat(providers.configured()).isEmpty();
            assertThat(providers.findByRegistrationId("google")).isNull();
            assertThat(providers).isEmpty();
        }
    }
}
