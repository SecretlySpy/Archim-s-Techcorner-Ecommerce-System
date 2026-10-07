package com.archimstechcorner.marketplace.features.payments.application.port;

import java.math.BigDecimal;

/**
 * T15: Provider-neutral PaymentGateway port.
 * Implementations: Mock for dev, real (e.g. Stripe, PayMongo) in adapters.
 * Never hold DB locks while calling.
 */
public interface PaymentGateway {
    /**
     * Create hosted checkout session.
     * @return session info with redirect URL or token for frontend.
     */
    HostedSession createHostedSession(String orderPublicId, BigDecimal amount, String currency, String successUrl, String cancelUrl);

    /**
     * Verify callback/webhook signature and return normalized result.
     */
    PaymentResult verifyCallback(String payload, String signature);

    /**
     * Issue refund.
     */
    RefundResult refund(String paymentId, BigDecimal amount);

    record HostedSession(String sessionId, String redirectUrl, String status) {}
    record PaymentResult(String paymentId, String status, BigDecimal amount, boolean success, String raw) {}
    record RefundResult(String refundId, String status, BigDecimal amount, boolean success) {}
}
