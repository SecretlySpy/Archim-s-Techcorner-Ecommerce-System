package com.archimstechcorner.marketplace.features.orders.api;

import com.archimstechcorner.marketplace.features.orders.application.CheckoutQuoteService;
import com.archimstechcorner.marketplace.features.orders.application.OrderCheckoutService;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final CheckoutQuoteService quoteService;
    private final OrderCheckoutService checkoutService;

    public CheckoutController(CheckoutQuoteService quoteService, OrderCheckoutService checkoutService) {
        this.quoteService = quoteService;
        this.checkoutService = checkoutService;
    }

    public record CreateQuoteRequest(
            @NotBlank String shippingAddressId,
            String shippingMethod
    ) {}

    public record CheckoutSubmitRequest(
            @NotBlank String quoteId,
            String idempotencyKey,
            String paymentMethod,
            String baseUrl
    ) {}

    @PostMapping("/quotes")
    public CheckoutQuoteService.QuoteResult createQuote(
            @Valid @RequestBody CreateQuoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return quoteService.generateQuote(
                principal.userId(),
                request.shippingAddressId(),
                request.shippingMethod()
        );
    }

    @PostMapping
    public OrderCheckoutService.CheckoutExecutionResult checkout(
            @Valid @RequestBody CheckoutSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        String effectiveKey = (idempotencyHeader != null && !idempotencyHeader.isBlank())
                ? idempotencyHeader : request.idempotencyKey();

        return checkoutService.checkout(
                principal.userId(),
                request.quoteId(),
                effectiveKey,
                request.paymentMethod(),
                request.baseUrl()
        );
    }
}
