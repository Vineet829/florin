package com.florin.config;

import com.florin.auth.JwtAuthenticationFilter;
import com.florin.auth.RestAuthenticationEntryPoint;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({
    AppProperties.class,
    DemoProperties.class,
    EncryptionProperties.class,
    OAuthProperties.class
})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, Customizer<OAuth2LoginConfigurer<HttpSecurity>> oauthSignIn, Environment environment)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/prometheus")
                        .access(onManagementPort(environment))
                        .requestMatchers(
                                "/actuator/health",
                                "/uploads/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/login/two-factor",
                                "/api/v1/auth/new_access_token",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify/**",
                                "/api/v1/auth/resend_email_token",
                                "/api/v1/auth/reset_password_request",
                                "/api/v1/auth/reset_password",
                                "/api/v1/auth/providers",
                                "/api/v1/auth/oauth2/**",
                                "/api/v1/public/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(oauthSignIn)
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> onManagementPort(Environment environment) {
        return (authentication, context) -> {
            boolean apart = ManagementPortType.get(environment) == ManagementPortType.DIFFERENT;
            Integer port = environment.getProperty("local.management.port", Integer.class);
            return new AuthorizationDecision(apart && port != null && context.getRequest().getLocalPort() == port);
        };
    }
}
