package com.archimstechcorner.marketplace.features.finance.api;

import com.archimstechcorner.marketplace.features.finance.application.RefundService;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/refunds")
public class CustomerRefundController {

    private final RefundService refundService;

    public CustomerRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    public record RequestRefundPayload(
            BigDecimal amount,
            @NotBlank String reason,
            String sellerOrderId
    ) {
        @com.fasterxml.jackson.annotation.JsonCreator
        public RequestRefundPayload(
                @com.fasterxml.jackson.annotation.JsonProperty("amount") BigDecimal amount,
                @com.fasterxml.jackson.annotation.JsonProperty("requestedAmount") BigDecimal requestedAmount,
                @com.fasterxml.jackson.annotation.JsonProperty("reason") String reason,
                @com.fasterxml.jackson.annotation.JsonProperty("sellerOrderId") String sellerOrderId
        ) {
            this(amount != null ? amount : requestedAmount, reason, sellerOrderId);
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public RefundService.RefundView requestRefund(
            @PathVariable String orderId,
            @Valid @RequestBody RequestRefundPayload payload,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return refundService.requestRefund(orderId, payload.amount(), payload.reason(),
                payload.sellerOrderId(), principal.userId());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RefundService.RefundView> listRefunds(
            @PathVariable String orderId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return refundService.listCustomerRefunds(orderId, principal.userId());
    }
}
