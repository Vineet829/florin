package com.florin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(String redirectBase, Provider google) {

    public record Provider(String clientId, String clientSecret, Endpoints endpoints) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record Endpoints(
            String authorizationUri, String tokenUri, String userInfoUri, String jwkSetUri, String issuerUri) {}
}
