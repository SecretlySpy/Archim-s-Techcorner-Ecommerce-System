package com.archimstechcorner.marketplace.features.payments.application;

import com.archimstechcorner.marketplace.features.finance.domain.model.SellerLedgerEntry;
import com.archimstechcorner.marketplace.features.finance.domain.repository.SellerLedgerEntryRepository;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.notifications.domain.model.CommunicationOutbox;
import com.archimstechcorner.marketplace.features.notifications.domain.repository.CommunicationOutboxRepository;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.repository.ParentOrderRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.SellerOrderRepository;
import com.archimstechcorner.marketplace.features.payments.application.port.PaymentGateway;
import com.archimstechcorner.marketplace.features.payments.domain.model.OrderPayment;
import com.archimstechcorner.marketplace.features.payments.domain.model.PaymentStatus;
import com.archimstechcorner.marketplace.features.payments.domain.repository.OrderPaymentRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final BigDecimal PLATFORM_COMMISSION_RATE = new BigDecimal("0.10"); // 10%

    private final PaymentGateway paymentGateway;
    private final OrderPaymentRepository orderPaymentRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final InventoryService inventoryService;
    private final SellerLedgerEntryRepository sellerLedgerEntryRepository;
    private final CommunicationOutboxRepository communicationOutboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentReconciliationService(PaymentGateway paymentGateway,
                                        OrderPaymentRepository orderPaymentRepository,
                                        ParentOrderRepository parentOrderRepository,
                                        SellerOrderRepository sellerOrderRepository,
                                        InventoryService inventoryService,
                                        SellerLedgerEntryRepository sellerLedgerEntryRepository,
                                        CommunicationOutboxRepository communicationOutboxRepository,
                                        ObjectMapper objectMapper,
                                        TransactionTemplate transactionTemplate) {
        this.paymentGateway = paymentGateway;
        this.orderPaymentRepository = orderPaymentRepository;
        this.parentOrderRepository = parentOrderRepository;
        this.sellerOrderRepository = sellerOrderRepository;
        this.inventoryService = inventoryService;
        this.sellerLedgerEntryRepository = sellerLedgerEntryRepository;
        this.communicationOutboxRepository = communicationOutboxRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public record WebhookProcessResult(String status, String orderId, String paymentId, String message) {}

    public WebhookProcessResult processWebhook(String provider, String payload, String signature) {
        log.info("Processing webhook for provider: {}", provider);

        PaymentGateway.PaymentResult verification = paymentGateway.verifyCallback(payload, signature);
        if (!verification.success()) {
            log.warn("Webhook verification failed for provider: {}", provider);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid webhook signature or payload.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Failed to parse webhook JSON payload", e);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Malformed webhook JSON payload.");
        }

        String orderPublicId = root.hasNonNull("orderPublicId") ? root.get("orderPublicId").asText() : null;
        String paymentPublicId = root.hasNonNull("paymentPublicId") ? root.get("paymentPublicId").asText() : null;
        String idempotencyKey = root.hasNonNull("idempotencyKey") ? root.get("idempotencyKey").asText() : null;
        String statusStr = root.hasNonNull("status") ? root.get("status").asText().toUpperCase() : "SUCCEEDED";
        String providerPaymentId = root.hasNonNull("providerPaymentId") ? root.get("providerPaymentId").asText() : verification.paymentId();

        return transactionTemplate.execute(txStatus -> {
            OrderPayment payment = null;
            if (idempotencyKey != null) {
                payment = orderPaymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            }
            if (payment == null && paymentPublicId != null) {
                try {
                    payment = orderPaymentRepository.findByPublicId(UUID.fromString(paymentPublicId)).orElse(null);
                } catch (IllegalArgumentException ignored) {}
            }
            if (payment == null && orderPublicId != null) {
                try {
                    ParentOrder parent = parentOrderRepository.findByPublicId(UUID.fromString(orderPublicId)).orElse(null);
                    if (parent != null) {
                        payment = orderPaymentRepository.findByOrderId(parent.getId()).orElse(null);
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            if (payment == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Associated order payment not found for webhook.");
            }

            ParentOrder order = payment.getOrder();

            // Idempotency: If already CAPTURED, return idempotent success
            if (payment.getStatus() == PaymentStatus.CAPTURED) {
                log.info("Payment {} already CAPTURED. Skipping duplicate processing.", payment.getPublicId());
                return new WebhookProcessResult("ALREADY_PROCESSED", order.getPublicId().toString(), payment.getPublicId().toString(), "Payment was already captured.");
            }

            boolean isSuccess = "SUCCEEDED".equalsIgnoreCase(statusStr)
                    || "PAID".equalsIgnoreCase(statusStr)
                    || "CAPTURED".equalsIgnoreCase(statusStr);

            if (isSuccess) {
                // 1. Mark payment CAPTURED
                payment.setStatus(PaymentStatus.CAPTURED);
                if (providerPaymentId != null) {
                    payment.setProviderPaymentId(providerPaymentId);
                }
                orderPaymentRepository.save(payment);

                // 2. Mark parent order and seller orders PAID
                order.setStatus(OrderStatus.PAID);
                parentOrderRepository.save(order);

                List<SellerOrder> sellerOrders = sellerOrderRepository.findByParentOrder_Id(order.getId());
                for (SellerOrder sellerOrder : sellerOrders) {
                    sellerOrder.setStatus(SellerOrderStatus.PAID);
                    sellerOrderRepository.save(sellerOrder);

                    // 3. Post seller ledger entry: earnings minus commission
                    BigDecimal subtotal = sellerOrder.getSubtotal();
                    BigDecimal commission = subtotal.multiply(PLATFORM_COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal netSellerEarning = subtotal.subtract(commission);

                    SellerLedgerEntry entry = SellerLedgerEntry.create(
                            sellerOrder.getSeller(),
                            sellerOrder,
                            "ORDER_EARNING",
                            netSellerEarning,
                            order.getCurrencyCode()
                    );
                    sellerLedgerEntryRepository.save(entry);

                    // 4. Record notification for seller in outbox
                    String sellerPayload = String.format("{\"sellerOrderId\":\"%s\",\"parentOrderId\":\"%s\",\"amount\":%s}",
                            sellerOrder.getPublicId(), order.getPublicId(), netSellerEarning);
                    String sellerEmail = sellerOrder.getSeller().getStaffMembers().isEmpty()
                            ? sellerOrder.getSeller().getPublicId() + "@seller.techcorner.local"
                            : sellerOrder.getSeller().getStaffMembers().get(0).getUser().getEmailDisplay();
                    communicationOutboxRepository.save(
                            CommunicationOutbox.create("SellerOrderPaid", sellerEmail,
                                    "New Paid Order Received: " + sellerOrder.getPublicId(), sellerPayload)
                    );
                }

                // 5. Consume inventory stock reservations
                inventoryService.consumeStockForOrder(order.getId(), order.getUser().getId());

                // 6. Record customer notification in outbox
                String customerPayload = String.format("{\"orderId\":\"%s\",\"total\":%s,\"currency\":\"%s\"}",
                        order.getPublicId(), order.getTotalAmount(), order.getCurrencyCode());
                communicationOutboxRepository.save(
                        CommunicationOutbox.create("OrderPaid", order.getUser().getEmail(),
                                "Order Confirmed: " + order.getPublicId(), customerPayload)
                );

                log.info("Order {} and payment {} successfully CAPTURED.", order.getPublicId(), payment.getPublicId());
                return new WebhookProcessResult("CAPTURED", order.getPublicId().toString(), payment.getPublicId().toString(), "Payment captured and order marked PAID.");
            } else {
                // Payment Failed / Cancelled / Expired
                payment.setStatus(PaymentStatus.FAILED);
                orderPaymentRepository.save(payment);

                order.setStatus(OrderStatus.CANCELLED);
                parentOrderRepository.save(order);

                List<SellerOrder> sellerOrders = sellerOrderRepository.findByParentOrder_Id(order.getId());
                for (SellerOrder sellerOrder : sellerOrders) {
                    sellerOrder.setStatus(SellerOrderStatus.CANCELLED);
                    sellerOrderRepository.save(sellerOrder);
                }

                // Release inventory stock reservations
                inventoryService.releaseStockForOrder(order.getId());

                // Record notification in outbox
                String customerPayload = String.format("{\"orderId\":\"%s\",\"status\":\"FAILED\"}", order.getPublicId());
                communicationOutboxRepository.save(
                        CommunicationOutbox.create("OrderPaymentFailed", order.getUser().getEmail(),
                                "Order Payment Failed: " + order.getPublicId(), customerPayload)
                );

                log.info("Order {} payment {} marked FAILED. Stock reservations released.", order.getPublicId(), payment.getPublicId());
                return new WebhookProcessResult("FAILED", order.getPublicId().toString(), payment.getPublicId().toString(), "Payment failed and reservations released.");
            }
        });
    }

    public PaymentStatusResponse getPaymentStatus(String orderPublicId, Long userId) {
        UUID orderUuid;
        try {
            orderUuid = UUID.fromString(orderPublicId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Invalid order ID.");
        }

        ParentOrder order = parentOrderRepository.findByPublicId(orderUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found."));

        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You are not authorized to view this order payment status.");
        }

        OrderPayment payment = orderPaymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No payment found for order."));

        return new PaymentStatusResponse(
                order.getPublicId().toString(),
                payment.getPublicId().toString(),
                payment.getStatus().name(),
                order.getStatus().name(),
                payment.getAmount(),
                payment.getCurrencyCode()
        );
    }

    public record PaymentStatusResponse(
            String orderId,
            String paymentId,
            String paymentStatus,
            String orderStatus,
            BigDecimal amount,
            String currency
    ) {}
}
