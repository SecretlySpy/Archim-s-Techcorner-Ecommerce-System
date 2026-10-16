package com.archimstechcorner.marketplace.features.payments.api;

import com.archimstechcorner.marketplace.features.payments.application.PaymentReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    private final PaymentReconciliationService reconciliationService;

    public PaymentWebhookController(PaymentReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/webhooks/{provider}")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @PathVariable("provider") String provider,
            @RequestBody String payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false, defaultValue = "valid_mock_signature") String signature) {

        PaymentReconciliationService.WebhookProcessResult result =
                reconciliationService.processWebhook(provider, payload, signature);

        return ResponseEntity.ok(Map.of(
                "status", result.status(),
                "orderId", result.orderId(),
                "paymentId", result.paymentId(),
                "message", result.message()
        ));
    }
}
