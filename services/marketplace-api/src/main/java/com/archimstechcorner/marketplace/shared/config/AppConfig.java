package com.archimstechcorner.marketplace.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Cross-cutting technical beans. A single injectable {@link Clock} keeps every timestamp in UTC and
 * lets tests substitute a fixed clock instead of relying on {@code Instant.now()} scattered in code.
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
