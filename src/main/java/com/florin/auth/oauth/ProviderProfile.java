package com.florin.auth.oauth;

import java.util.Locale;
import org.springframework.security.oauth2.core.user.OAuth2User;

public record ProviderProfile(
        SignInProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        boolean emailHosted,
        String firstName,
        String lastName,
        String handle) {

    static ProviderProfile of(String registrationId, OAuth2User user) {
        SignInProvider provider =
                SignInProvider.of(registrationId).orElseThrow(() -> new OAuthSignInException("failed"));
        return switch (provider) {
            case GOOGLE -> google(user);
        };
    }

    private static ProviderProfile google(OAuth2User user) {
        String email = text(user.getAttribute("email"));
        Object verified = user.getAttribute("email_verified");
        return new ProviderProfile(
                SignInProvider.GOOGLE,
                required(text(user.getAttribute("sub"))),
                email,
                Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified)),
                email != null && (text(user.getAttribute("hd")) != null || gmail(email)),
                text(user.getAttribute("given_name")),
                text(user.getAttribute("family_name")),
                email == null ? null : email.substring(0, Math.max(email.indexOf('@'), 0)));
    }

    private static boolean gmail(String email) {
        String address = email.toLowerCase(Locale.ROOT);
        return address.endsWith("@gmail.com") || address.endsWith("@googlemail.com");
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static String required(String subject) {
        if (subject == null) {
            throw new OAuthSignInException("failed");
        }
        return subject;
    }
}
