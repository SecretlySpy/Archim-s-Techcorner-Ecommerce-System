package com.archimstechcorner.marketplace.features.fulfillment.application;

import com.archimstechcorner.marketplace.features.fulfillment.domain.model.Shipment;
import com.archimstechcorner.marketplace.features.fulfillment.domain.repository.ShipmentRepository;
import com.archimstechcorner.marketplace.features.notifications.domain.model.CommunicationOutbox;
import com.archimstechcorner.marketplace.features.notifications.domain.repository.CommunicationOutboxRepository;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.repository.ParentOrderRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.SellerOrderRepository;
import com.archimstechcorner.marketplace.features.sellers.application.SellerService;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FulfillmentService {

    private final ShipmentRepository shipmentRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final SellerService sellerService;
    private final CommunicationOutboxRepository outboxRepository;

    public FulfillmentService(ShipmentRepository shipmentRepository,
                              SellerOrderRepository sellerOrderRepository,
                              ParentOrderRepository parentOrderRepository,
                              SellerService sellerService,
                              CommunicationOutboxRepository outboxRepository) {
        this.shipmentRepository = shipmentRepository;
        this.sellerOrderRepository = sellerOrderRepository;
        this.parentOrderRepository = parentOrderRepository;
        this.sellerService = sellerService;
        this.outboxRepository = outboxRepository;
    }

    public record ShipmentView(
            String id,
            String sellerOrderId,
            String courierCode,
            String trackingNumber,
            String status,
            Instant shippedAt,
            Instant deliveredAt
    ) {}

    public record SellerOrderSummary(
            String id,
            String parentOrderId,
            String status,
            java.math.BigDecimal subtotal,
            Instant createdAt,
            String customerEmail,
            String recipientName,
            String destinationCity,
            int itemCount
    ) {}

    @Transactional
    public ShipmentView createShipment(String sellerOrderPublicId, String courierCode, String trackingNumber, Long userId) {
        UUID orderUuid;
        try {
            orderUuid = UUID.fromString(sellerOrderPublicId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Invalid seller order ID.");
        }

        SellerOrder sellerOrder = sellerOrderRepository.findByPublicId(orderUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Seller order not found."));

        // Verify caller belongs to the seller of this order
        sellerService.requireOwnership(sellerOrder.getSeller().getPublicId(), userId);

        // Validate state
        if (sellerOrder.getStatus() != SellerOrderStatus.PAID && sellerOrder.getStatus() != SellerOrderStatus.PROCESSING) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Only paid or processing orders can be shipped.");
        }

        if (courierCode == null || courierCode.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Courier code is required.");
        }

        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Tracking number is required.");
        }

        if (!shipmentRepository.findByCourierCodeAndTrackingNumber(courierCode, trackingNumber).isEmpty()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "A shipment with this courier and tracking number already exists.");
        }

        Shipment shipment = Shipment.create(sellerOrder, courierCode, trackingNumber);
        shipment = shipmentRepository.save(shipment);

        sellerOrder.setStatus(SellerOrderStatus.SHIPPED);
        sellerOrderRepository.save(sellerOrder);

        // Update Parent Order status (PARTIALLY_SHIPPED or SHIPPED)
        ParentOrder parent = sellerOrder.getParentOrder();
        List<SellerOrder> allSellerOrders = sellerOrderRepository.findByParentOrder_Id(parent.getId());
        boolean allShipped = allSellerOrders.stream().allMatch(so -> so.getStatus() == SellerOrderStatus.SHIPPED);
        if (allShipped) {
            parent.setStatus(OrderStatus.SHIPPED);
        } else {
            parent.setStatus(OrderStatus.PARTIALLY_SHIPPED);
        }
        parentOrderRepository.save(parent);

        // Record outbox dispatch notification
        String payload = String.format("{\"orderId\":\"%s\",\"sellerOrderId\":\"%s\",\"courier\":\"%s\",\"trackingNumber\":\"%s\"}",
                parent.getPublicId(), sellerOrder.getPublicId(), courierCode, trackingNumber);
        outboxRepository.save(
                CommunicationOutbox.create("ShipmentDispatched", parent.getUser().getEmail(),
                        "Your shipment has dispatched via " + courierCode, payload)
        );

        return toView(shipment);
    }

    @Transactional(readOnly = true)
    public List<ShipmentView> getSellerOrderShipments(String sellerOrderPublicId, Long userId) {
        UUID orderUuid = UUID.fromString(sellerOrderPublicId);
        SellerOrder sellerOrder = sellerOrderRepository.findByPublicId(orderUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Seller order not found."));
        sellerService.requireOwnership(sellerOrder.getSeller().getPublicId(), userId);

        return shipmentRepository.findBySellerOrder_Id(sellerOrder.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShipmentView> getParentOrderShipments(String parentOrderPublicId, Long userId) {
        UUID parentUuid = UUID.fromString(parentOrderPublicId);
        ParentOrder parent = parentOrderRepository.findByPublicId(parentUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found."));

        if (!parent.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not authorized to view shipments for this order.");
        }

        List<SellerOrder> sellerOrders = sellerOrderRepository.findByParentOrder_Id(parent.getId());
        List<ShipmentView> result = new ArrayList<>();
        for (SellerOrder so : sellerOrders) {
            result.addAll(shipmentRepository.findBySellerOrder_Id(so.getId()).stream().map(this::toView).toList());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SellerOrderSummary> listSellerOrders(String sellerPublicId, Long userId) {
        Seller seller = sellerService.requireOwnership(sellerPublicId, userId);
        return sellerOrderRepository.findBySellerOrderByCreatedAtDesc(seller).stream()
                .map(so -> {
                    var addr = so.getParentOrder().getShippingAddress();
                    return new SellerOrderSummary(
                            so.getPublicId().toString(),
                            so.getParentOrder().getPublicId().toString(),
                            so.getStatus().name(),
                            so.getSubtotal(),
                            so.getCreatedAt(),
                            so.getParentOrder().getUser().getEmail(),
                            addr != null ? addr.getRecipientName() : "N/A",
                            addr != null ? addr.getCity() : "N/A",
                            so.getItems().size()
                    );
                })
                .toList();
    }

    private ShipmentView toView(Shipment s) {
        return new ShipmentView(
                s.getPublicId().toString(),
                s.getSellerOrder().getPublicId().toString(),
                s.getCourierCode(),
                s.getTrackingNumber(),
                s.getStatus(),
                s.getShippedAt(),
                s.getDeliveredAt()
        );
    }
}
