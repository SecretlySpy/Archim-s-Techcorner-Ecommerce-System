package com.archimstechcorner.marketplace.features.authentication.application.port;

/**
 * Outbound port for transactional identity emails. Implementations are swappable (log sink for dev,
 * SMTP/MailHog locally, a provider adapter in production) without changing the application services.
 */
public interface EmailSender {

    void sendVerification(String toEmail, String rawToken);

    void sendPasswordReset(String toEmail, String rawToken);
}
