package com.archimstechcorner.marketplace.features.payments.infrastructure;

import com.archimstechcorner.marketplace.features.payments.application.port.PaymentGateway;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock implementation for development/testing.
 * Simulates hosted payment success.
 * In real: use provider SDK, sign webhooks, etc.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public HostedSession createHostedSession(String orderPublicId, BigDecimal amount, String currency, String successUrl, String cancelUrl) {
        String sessionId = "mock_sess_" + UUID.randomUUID().toString().substring(0, 8);
        // In real app, frontend would redirect to this or use iframe
        String redirect = successUrl + "?session=" + sessionId + "&order=" + orderPublicId;
        return new HostedSession(sessionId, redirect, "CREATED");
    }

    @Override
    public PaymentResult verifyCallback(String payload, String signature) {
        if ("invalid_signature".equalsIgnoreCase(signature)) {
            return new PaymentResult(null, "FAILED", BigDecimal.ZERO, false, payload);
        }
        if (payload == null || payload.isEmpty()) {
            return new PaymentResult(null, "FAILED", BigDecimal.ZERO, false, payload);
        }
        // Simulate
        return new PaymentResult("mock_pay_" + UUID.randomUUID().toString().substring(0,8), "SUCCEEDED", new BigDecimal("100.00"), true, payload);
    }

    @Override
    public RefundResult refund(String paymentId, BigDecimal amount) {
        return new RefundResult("mock_ref_" + UUID.randomUUID().toString().substring(0,8), "SUCCEEDED", amount, true);
    }
}
