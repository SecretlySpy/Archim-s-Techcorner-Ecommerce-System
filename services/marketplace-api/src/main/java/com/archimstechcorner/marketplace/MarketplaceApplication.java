package com.archimstechcorner.marketplace;

import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Entry point for the Archim's TechCorner modular monolith.
 *
 * <p>{@code @EnableMethodSecurity} turns on {@code @PreAuthorize} so endpoints can declare the
 * fine-grained permission they require (see documentation/security/rbac-matrix.md). Feature modules
 * live under {@code features/*}; cross-cutting technical concerns live under {@code shared/*}.
 */
@SpringBootApplication
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class MarketplaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketplaceApplication.class, args);
    }
}
