package com.archimstechcorner.marketplace.features.fulfillment.api;

import com.archimstechcorner.marketplace.features.fulfillment.application.FulfillmentService;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    public record CreateShipmentRequest(
            @NotBlank String courierCode,
            @NotBlank String trackingNumber
    ) {}

    @PostMapping("/seller-orders/{sellerOrderId}/shipments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public FulfillmentService.ShipmentView createShipment(
            @PathVariable String sellerOrderId,
            @Valid @RequestBody CreateShipmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return fulfillmentService.createShipment(sellerOrderId, request.courierCode(), request.trackingNumber(), principal.userId());
    }

    @GetMapping("/seller-orders/{sellerOrderId}/shipments")
    @PreAuthorize("isAuthenticated()")
    public List<FulfillmentService.ShipmentView> getSellerOrderShipments(
            @PathVariable String sellerOrderId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return fulfillmentService.getSellerOrderShipments(sellerOrderId, principal.userId());
    }

    @GetMapping("/sellers/{sellerId}/orders")
    @PreAuthorize("isAuthenticated()")
    public List<FulfillmentService.SellerOrderSummary> listSellerOrders(
            @PathVariable String sellerId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return fulfillmentService.listSellerOrders(sellerId, principal.userId());
    }

    @GetMapping("/orders/{orderId}/shipments")
    @PreAuthorize("isAuthenticated()")
    public List<FulfillmentService.ShipmentView> getParentOrderShipments(
            @PathVariable String orderId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return fulfillmentService.getParentOrderShipments(orderId, principal.userId());
    }
}
