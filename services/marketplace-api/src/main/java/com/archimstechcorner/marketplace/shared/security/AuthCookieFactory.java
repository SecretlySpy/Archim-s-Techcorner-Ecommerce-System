package com.archimstechcorner.marketplace.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Builds and reads the auth cookies. Tokens live in {@code Secure}, {@code HttpOnly},
 * {@code SameSite=Lax} cookies so browser JavaScript cannot read them (auth design §7).
 */
@Component
public class AuthCookieFactory {

    public static final String ACCESS_COOKIE = "atc_access";
    public static final String REFRESH_COOKIE = "atc_refresh";

    private final AuthProperties properties;

    public AuthCookieFactory(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie accessCookie(String value) {
        return base(ACCESS_COOKIE, value, Duration.ofSeconds(properties.getAccessTokenTtlSeconds())).build();
    }

    public ResponseCookie refreshCookie(String value) {
        // Restrict the refresh cookie to the token endpoint so it is not sent on every request.
        return base(REFRESH_COOKIE, value, Duration.ofSeconds(properties.getRefreshTokenTtlSeconds()))
                .path("/api/v1/auth")
                .build();
    }

    /** Zero-age cookies that instruct the browser to delete the pair (logout). */
    public ResponseCookie expiredAccessCookie() {
        return base(ACCESS_COOKIE, "", Duration.ZERO).build();
    }

    public ResponseCookie expiredRefreshCookie() {
        return base(REFRESH_COOKIE, "", Duration.ZERO).path("/api/v1/auth").build();
    }

    public Optional<String> readRefresh(HttpServletRequest request) {
        return read(request, REFRESH_COOKIE);
    }

    public Optional<String> readAccess(HttpServletRequest request) {
        return read(request, ACCESS_COOKIE);
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .domain(properties.getCookieDomain())
                .path("/")
                .maxAge(maxAge);
    }

    private Optional<String> read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
