package com.archimstechcorner.marketplace.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates short-lived access-token JWTs (HS256). The token is self-contained: it carries
 * the principal's id, public id, email, security version, and authorities, so normal requests are
 * authorized without a database read. Revocation within the short TTL is handled by refresh rotation.
 */
@Service
public class AccessTokenService {

    private final SecretKey signingKey;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // HS256 requires a >= 256-bit key; a short dev secret would throw here, failing fast.
        this.signingKey = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a signed access token for the given principal. */
    public String issue(Long userId, UUID publicId, String email, int securityVersion, List<String> authorities) {
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds(properties.getAccessTokenTtlSeconds());
        return Jwts.builder()
                .subject(publicId.toString())          // stable, non-sequential external identity
                .claim("uid", userId)                  // internal id for ownership checks
                .claim("email", email)
                .claim("sv", securityVersion)          // invalidated when the user's security_version bumps
                .claim("authorities", authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /** Validates signature + expiry and reconstructs the principal, or empty if the token is invalid. */
    @SuppressWarnings("unchecked")
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    // Use the injected clock for both issuing and parsing so tests and production
                    // expiry decisions share one authoritative time source.
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = ((Number) claims.get("uid")).longValue();
            int securityVersion = ((Number) claims.get("sv")).intValue();
            List<String> authorities = (List<String>) claims.getOrDefault("authorities", List.of());
            return Optional.of(new AuthenticatedUser(
                    userId,
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    securityVersion,
                    authorities));
        } catch (JwtException | IllegalArgumentException | ClassCastException ex) {
            // Expired, tampered, or malformed — treated as unauthenticated (no detail leaked).
            return Optional.empty();
        }
    }

    /** Builds a signed short-lived MFA challenge token. */
    public String issueMfaChallenge(Long userId) {
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds(300); // 5 minutes to complete MFA
        return Jwts.builder()
                .claim("mfa_uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /** Validates an MFA challenge token and returns the userId, or empty if invalid. */
    public Optional<Long> parseMfaChallenge(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(((Number) claims.get("mfa_uid")).longValue());
        } catch (JwtException | IllegalArgumentException | ClassCastException ex) {
            return Optional.empty();
        }
    }
}
