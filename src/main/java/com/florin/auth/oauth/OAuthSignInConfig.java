package com.florin.auth.oauth;

import com.florin.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

@Configuration
public class OAuthSignInConfig {

    static final String AUTHORIZATION_PATH = "/api/v1/auth/oauth2/authorization";

    @Bean
    Customizer<OAuth2LoginConfigurer<HttpSecurity>> oauthSignIn(
            ConfiguredProviders providers,
            CookieAuthorizationRequests requests,
            OAuthSuccessHandler successHandler,
            OAuthFailureHandler failureHandler,
            AppProperties properties) {
        return login -> login.loginPage(properties.site().clientUrl() + "/login")
                .clientRegistrationRepository(providers)
                .authorizedClientRepository(new DiscardedAuthorizedClients())
                .authorizationEndpoint(endpoint -> endpoint.baseUri(AUTHORIZATION_PATH)
                        .authorizationRequestRepository(requests)
                        .authorizationRequestResolver(new ConfiguredRequestResolver(providers, AUTHORIZATION_PATH)))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(ConfiguredProviders.CALLBACK_PATH + "*"))
                .successHandler(successHandler)
                .failureHandler(failureHandler);
    }
}
