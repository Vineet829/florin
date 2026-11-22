package com.florin.auth.oauth;

import com.florin.config.AppProperties;
import com.florin.metrics.AppMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuthFailureHandler implements AuthenticationFailureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthFailureHandler.class);

    private final AppMetrics metrics;
    private final String clientUrl;

    public OAuthFailureHandler(AppMetrics metrics, AppProperties properties) {
        this.metrics = metrics;
        this.clientUrl = properties.site().clientUrl();
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String code = exception instanceof OAuth2AuthenticationException oauth
                ? code(oauth.getError().getErrorCode())
                : "failed";
        if (code.equals("failed")) {
            if (exception instanceof OAuth2AuthenticationException oauth) {
                OAuth2Error error = oauth.getError();
                LOGGER.warn("A sign-in with a provider failed: {}{}", error.getErrorCode(),
                        error.getDescription() == null ? "" : " (" + error.getDescription() + ")");
            } else {
                LOGGER.warn("A sign-in with a provider failed", exception);
            }
        }
        String provider = provider(request);
        if (!provider.isEmpty()) {
            metrics.signInFailed(provider);
        }
        response.sendRedirect(refused(clientUrl, code, provider));
    }

    static String code(String error) {
        return switch (error) {
            case "access_denied" -> "cancelled";
            case "authorization_request_not_found", "invalid_state_parameter" -> "expired";
            default -> "failed";
        };
    }

    static String refused(String clientUrl, String code, String provider) {
        return clientUrl + "/login?oauth_error=" + code + (provider.isEmpty() ? "" : "&provider=" + provider);
    }

    private static String provider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return SignInProvider.of(uri.substring(uri.lastIndexOf('/') + 1)).map(SignInProvider::id).orElse("");
    }
}
