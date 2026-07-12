package com.archimstechcorner.marketplace.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing strategy. A delegating encoder stores an algorithm prefix (e.g. {@code {bcrypt}})
 * with each hash, so the parameters can be upgraded and old hashes still verify — the transparent
 * re-hash path required by the auth design (Argon2id is the target; bcrypt is the approved default).
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
