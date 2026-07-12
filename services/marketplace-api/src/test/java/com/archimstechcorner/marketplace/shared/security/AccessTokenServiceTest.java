package com.archimstechcorner.marketplace.shared.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for JWT issue/parse — verifies round-trip, tamper rejection, and expiry. */
class AccessTokenServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);

    private AuthProperties propertiesWithTtl(long ttlSeconds) {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test_secret_key_that_is_definitely_long_enough_1234");
        properties.setAccessTokenTtlSeconds(ttlSeconds);
        return properties;
    }

    @Test
    void issuesAndParsesRoundTrip() {
        AccessTokenService service = new AccessTokenService(propertiesWithTtl(900), FIXED);
        UUID publicId = UUID.randomUUID();

        String token = service.issue(42L, publicId, "buyer@example.com", 3,
                List.of("ROLE_CUSTOMER", "users.read.any"));
        AuthenticatedUser parsed = service.parse(token).orElseThrow();

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.publicId()).isEqualTo(publicId);
        assertThat(parsed.email()).isEqualTo("buyer@example.com");
        assertThat(parsed.securityVersion()).isEqualTo(3);
        assertThat(parsed.authorities()).contains("ROLE_CUSTOMER", "users.read.any");
    }

    @Test
    void rejectsMalformedToken() {
        AccessTokenService service = new AccessTokenService(propertiesWithTtl(900), FIXED);
        assertThat(service.parse("not.a.real.jwt")).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        // A negative TTL means the token is already expired at issue time.
        AccessTokenService service = new AccessTokenService(propertiesWithTtl(-1), FIXED);
        String token = service.issue(1L, UUID.randomUUID(), "x@y.com", 0, List.of());
        assertThat(service.parse(token)).isEmpty();
    }
}
