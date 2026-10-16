package com.archimstechcorner.marketplace.features.orders.api;

import com.archimstechcorner.marketplace.features.orders.application.OrderCheckoutService;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderCheckoutService checkoutService;
    private final com.archimstechcorner.marketplace.features.payments.application.PaymentReconciliationService reconciliationService;

    public OrderController(OrderCheckoutService checkoutService,
                           com.archimstechcorner.marketplace.features.payments.application.PaymentReconciliationService reconciliationService) {
        this.checkoutService = checkoutService;
        this.reconciliationService = reconciliationService;
    }

    public record OrderSummaryResponse(
            String id,
            BigDecimal totalAmount,
            String currencyCode,
            String status,
            Instant createdAt
    ) {}

    public record OrderDetailResponse(
            String id,
            BigDecimal totalAmount,
            String currencyCode,
            String status,
            Instant createdAt,
            AddressSnapshotResponse shippingAddress,
            List<SellerOrderResponse> sellerOrders
    ) {}

    public record AddressSnapshotResponse(
            String recipientName,
            String phoneNumber,
            String streetLine1,
            String streetLine2,
            String city,
            String provinceState,
            String postalCode,
            String countryCode
    ) {}

    public record SellerOrderResponse(
            String id,
            String sellerName,
            BigDecimal subtotal,
            String status,
            List<OrderItemResponse> items
    ) {}

    public record OrderItemResponse(
            Long variantId,
            String productName,
            String variantName,
            String sku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {}

    @GetMapping
    public List<OrderSummaryResponse> listOrders(@AuthenticationPrincipal AuthenticatedUser principal) {
        return checkoutService.listUserOrders(principal.userId()).stream()
                .map(o -> new OrderSummaryResponse(
                        o.getPublicId().toString(),
                        o.getTotalAmount(),
                        o.getCurrencyCode(),
                        o.getStatus().name(),
                        o.getCreatedAt()
                ))
                .toList();
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrder(@PathVariable String orderId,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        ParentOrder o = checkoutService.getOrder(orderId, principal.userId());

        AddressSnapshotResponse addr = null;
        if (o.getShippingAddress() != null) {
            var a = o.getShippingAddress();
            addr = new AddressSnapshotResponse(
                    a.getRecipientName(),
                    a.getPhone(),
                    a.getLine1(),
                    a.getLine2(),
                    a.getCity(),
                    a.getState(),
                    a.getPostalCode(),
                    a.getCountry()
            );
        }

        List<SellerOrderResponse> sellers = o.getSellerOrders().stream()
                .map(so -> new SellerOrderResponse(
                        so.getPublicId().toString(),
                        so.getSeller().getName(),
                        so.getSubtotal(),
                        so.getStatus().name(),
                        so.getItems().stream()
                                .map(item -> new OrderItemResponse(
                                        item.getVariantId(),
                                        item.getProductNameSnapshot(),
                                        item.getVariantNameSnapshot(),
                                        item.getSkuSnapshot(),
                                        item.getUnitPriceSnapshot(),
                                        item.getQuantity(),
                                        item.getLineTotal()
                                ))
                                .toList()
                ))
                .toList();

        return new OrderDetailResponse(
                o.getPublicId().toString(),
                o.getTotalAmount(),
                o.getCurrencyCode(),
                o.getStatus().name(),
                o.getCreatedAt(),
                addr,
                sellers
        );
    }

    @GetMapping("/{orderId}/payment-status")
    public com.archimstechcorner.marketplace.features.payments.application.PaymentReconciliationService.PaymentStatusResponse getPaymentStatus(
            @PathVariable String orderId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return reconciliationService.getPaymentStatus(orderId, principal.userId());
    }
}
