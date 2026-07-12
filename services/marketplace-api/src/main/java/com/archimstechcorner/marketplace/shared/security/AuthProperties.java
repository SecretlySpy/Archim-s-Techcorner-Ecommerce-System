package com.archimstechcorner.marketplace.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalized auth configuration (bound from the {@code auth.*} block in application.yml / env).
 * Secrets are injected at runtime and never committed; defaults here are dev-only fallbacks.
 */
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** HS256 signing key for access-token JWTs. MUST be >= 32 bytes; rotate + externalize in prod. */
    private String jwtSecret = "change_me_dev_only_min_32_bytes_long_secret_value";
    private long accessTokenTtlSeconds = 900;          // 15 minutes
    private long refreshTokenTtlSeconds = 1_209_600;   // 14 days
    private boolean cookieSecure = false;              // true behind HTTPS
    private String cookieDomain = "localhost";
    private List<String> allowedOrigins = List.of("http://localhost:3000");
    private int lockoutThreshold = 5;                  // failed logins before lock
    private int lockoutMinutes = 15;
    private int verificationTokenTtlMinutes = 1_440;   // 24h
    private int resetTokenTtlMinutes = 60;             // 1h

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public long getAccessTokenTtlSeconds() { return accessTokenTtlSeconds; }
    public void setAccessTokenTtlSeconds(long v) { this.accessTokenTtlSeconds = v; }

    public long getRefreshTokenTtlSeconds() { return refreshTokenTtlSeconds; }
    public void setRefreshTokenTtlSeconds(long v) { this.refreshTokenTtlSeconds = v; }

    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean v) { this.cookieSecure = v; }

    public String getCookieDomain() { return cookieDomain; }
    public void setCookieDomain(String v) { this.cookieDomain = v; }

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> v) { this.allowedOrigins = v; }

    public int getLockoutThreshold() { return lockoutThreshold; }
    public void setLockoutThreshold(int v) { this.lockoutThreshold = v; }

    public int getLockoutMinutes() { return lockoutMinutes; }
    public void setLockoutMinutes(int v) { this.lockoutMinutes = v; }

    public int getVerificationTokenTtlMinutes() { return verificationTokenTtlMinutes; }
    public void setVerificationTokenTtlMinutes(int v) { this.verificationTokenTtlMinutes = v; }

    public int getResetTokenTtlMinutes() { return resetTokenTtlMinutes; }
    public void setResetTokenTtlMinutes(int v) { this.resetTokenTtlMinutes = v; }
}
