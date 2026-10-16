package com.archimstechcorner.marketplace.features.authentication.infrastructure;

import com.archimstechcorner.marketplace.features.authentication.application.port.EmailSender;
import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development {@link EmailSender} that logs the action link instead of sending real mail, so the
 * verification/reset flow is testable locally (inspect the log or MailHog). A production adapter
 * implementing the same port replaces this without touching the application layer.
 *
 * <p>Restricted to non-production profiles so production fails fast if an email provider is missing.
 */
@Component
@Profile({"local", "dev", "test", "default"})
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    private final String webBaseUrl;

    public LogEmailSender(AuthProperties properties) {
        // Use the first allowed origin as the web base for the emailed link.
        this.webBaseUrl = properties.getAllowedOrigins().isEmpty()
                ? "http://localhost:3000"
                : properties.getAllowedOrigins().get(0);
    }

    @Override
    public void sendVerification(String toEmail, String rawToken) {
        String tokenPreview = rawToken != null && rawToken.length() > 6 
                ? rawToken.substring(0, 6) + "...[REDACTED]" 
                : "[REDACTED]";
        log.info("[email:verify] to={} link={}/verify-email?token={}", toEmail, webBaseUrl, tokenPreview);
    }

    @Override
    public void sendPasswordReset(String toEmail, String rawToken) {
        String tokenPreview = rawToken != null && rawToken.length() > 6 
                ? rawToken.substring(0, 6) + "...[REDACTED]" 
                : "[REDACTED]";
        log.info("[email:reset] to={} link={}/reset-password?token={}", toEmail, webBaseUrl, tokenPreview);
    }
}
