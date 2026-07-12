package com.archimstechcorner.marketplace.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for the password hashing strategy — no Spring context or database needed. */
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void hashesAndVerifiesPassword() {
        String raw = "correct horse battery staple";

        String hash = encoder.encode(raw);

        assertThat(hash).isNotEqualTo(raw);                 // never stored in cleartext
        assertThat(encoder.matches(raw, hash)).isTrue();    // the right password verifies
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void tagsHashWithAlgorithmForFutureUpgrades() {
        // The delegating encoder prefixes the algorithm so parameters can be upgraded later.
        assertThat(encoder.encode("anything")).startsWith("{bcrypt}");
    }
}
