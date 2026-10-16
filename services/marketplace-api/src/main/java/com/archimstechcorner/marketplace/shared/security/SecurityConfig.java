package com.archimstechcorner.marketplace.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * HTTP security policy: stateless, deny-by-default, JSON errors.
 *
 * <ul>
 *   <li>Public: the pre-auth POST endpoints (register/login/refresh/verify/reset), health, and API docs.</li>
 *   <li>Everything else requires authentication; fine-grained permissions are enforced with
 *       {@code @PreAuthorize} at the method (see rbac-matrix.md).</li>
 *   <li>Spring's CSRF filter is disabled because sessions are stateless JWT cookies; CSRF is instead
 *       mitigated by {@code SameSite=Lax} cookies plus {@link OriginCheckFilter}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register/**",
            "/api/v1/auth/login",
            "/api/v1/auth/login/mfa",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/email/verify/resend",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/payments/webhooks/**"
    };

    private static final String[] PUBLIC_GET = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieAuthenticationFilter cookieAuthenticationFilter,
            OriginCheckFilter originCheckFilter,
            RestAuthEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(PUBLIC_POST) // Don't require CSRF for login/register as they establish the session
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        // Public catalog browsing (guests). Only GET; write endpoints keep @PreAuthorize.
                        // Product detail returns published (ACTIVE) products only — enforced in the service.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/catalog/categories",
                                "/api/v1/catalog/brands",
                                "/api/v1/catalog/products/**",
                                "/api/v1/sellers/*").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Filters
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(originCheckFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
