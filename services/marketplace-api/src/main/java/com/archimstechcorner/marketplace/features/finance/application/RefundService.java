package com.archimstechcorner.marketplace.features.finance.application;

import com.archimstechcorner.marketplace.features.finance.domain.model.RefundRequest;
import com.archimstechcorner.marketplace.features.finance.domain.model.SellerLedgerEntry;
import com.archimstechcorner.marketplace.features.finance.domain.repository.RefundRequestRepository;
import com.archimstechcorner.marketplace.features.finance.domain.repository.SellerLedgerEntryRepository;
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
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderPaymentRepository paymentRepository;
    private final SellerLedgerEntryRepository ledgerEntryRepository;
    private final CommunicationOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;

    public RefundService(RefundRequestRepository refundRequestRepository,
                         ParentOrderRepository parentOrderRepository,
                         SellerOrderRepository sellerOrderRepository,
                         OrderPaymentRepository paymentRepository,
                         SellerLedgerEntryRepository ledgerEntryRepository,
                         CommunicationOutboxRepository outboxRepository,
                         UserRepository userRepository,
                         PaymentGateway paymentGateway) {
        this.refundRequestRepository = refundRequestRepository;
        this.parentOrderRepository = parentOrderRepository;
        this.sellerOrderRepository = sellerOrderRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
    }

    public record RefundView(
            String id,
            String orderId,
            String sellerOrderId,
            BigDecimal requestedAmount,
            String reason,
            String status,
            Instant createdAt
    ) {}

    @Transactional
    public RefundView requestRefund(String parentOrderPublicId, BigDecimal requestedAmount,
                                    String reason, String sellerOrderPublicId, Long customerUserId) {
        UUID parentUuid;
        try {
            parentUuid = UUID.fromString(parentOrderPublicId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Invalid order ID.");
        }

        ParentOrder order = parentOrderRepository.findByPublicId(parentUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found."));

        if (!order.getUser().getId().equals(customerUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not authorized to request refund for this order.");
        }

        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.PARTIALLY_SHIPPED
                && order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.DELIVERED) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Only paid or fulfilled orders can be refunded.");
        }

        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Requested refund amount must be positive.");
        }

        if (requestedAmount.compareTo(order.getTotalAmount()) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Requested amount cannot exceed order total.");
        }

        SellerOrder sellerOrder = null;
        if (sellerOrderPublicId != null && !sellerOrderPublicId.isBlank()) {
            try {
                sellerOrder = sellerOrderRepository.findByPublicId(UUID.fromString(sellerOrderPublicId)).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }

        RefundRequest req = RefundRequest.create(order, sellerOrder, requestedAmount, reason);
        req = refundRequestRepository.save(req);

        // Record outbox event for administrative audit
        String payload = String.format("{\"refundId\":\"%s\",\"orderId\":\"%s\",\"amount\":%s,\"reason\":\"%s\"}",
                req.getPublicId(), order.getPublicId(), requestedAmount, reason);
        outboxRepository.save(
                CommunicationOutbox.create("RefundRequested", order.getUser().getEmail(),
                        "Refund Request Received: " + order.getPublicId(), payload)
        );

        return toView(req);
    }

    @Transactional(readOnly = true)
    public List<RefundView> listCustomerRefunds(String parentOrderPublicId, Long customerUserId) {
        UUID parentUuid = UUID.fromString(parentOrderPublicId);
        ParentOrder order = parentOrderRepository.findByPublicId(parentUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found."));

        if (!order.getUser().getId().equals(customerUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not authorized to view refunds for this order.");
        }

        return refundRequestRepository.findByParentOrder_Id(order.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundView> listPendingRefunds() {
        return refundRequestRepository.findByStatus("PENDING").stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public RefundView approveRefund(String refundPublicId, Long approverUserId) {
        UUID refundUuid = UUID.fromString(refundPublicId);
        RefundRequest req = refundRequestRepository.findByPublicId(refundUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Refund request not found."));

        if (!"PENDING".equals(req.getStatus())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Refund request is already processed.");
        }

        User approver = userRepository.findById(approverUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Approver not found."));

        req.approve(approver);
        refundRequestRepository.save(req);

        ParentOrder order = req.getParentOrder();
        OrderPayment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);

        // Trigger payment provider refund call
        if (payment != null && payment.getProviderPaymentId() != null) {
            paymentGateway.refund(payment.getProviderPaymentId(), req.getRequestedAmount());
            if (req.getRequestedAmount().compareTo(order.getTotalAmount()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                order.setStatus(OrderStatus.REFUNDED);
                parentOrderRepository.save(order);
            }
        }

        // Post seller ledger reversal entry
        if (req.getSellerOrder() != null) {
            SellerOrder so = req.getSellerOrder();
            BigDecimal deduction = req.getRequestedAmount().negate();
            ledgerEntryRepository.save(
                    SellerLedgerEntry.create(so.getSeller(), so, "REFUND_DEDUCTION", deduction, order.getCurrencyCode())
            );
            if (req.getRequestedAmount().compareTo(so.getSubtotal()) >= 0) {
                so.setStatus(SellerOrderStatus.REFUNDED);
                sellerOrderRepository.save(so);
            }
        } else {
            // Reversal distributed across seller orders
            List<SellerOrder> sellerOrders = sellerOrderRepository.findByParentOrder_Id(order.getId());
            for (SellerOrder so : sellerOrders) {
                BigDecimal share = req.getRequestedAmount().negate().divide(BigDecimal.valueOf(sellerOrders.size()), 2, java.math.RoundingMode.HALF_UP);
                ledgerEntryRepository.save(
                        SellerLedgerEntry.create(so.getSeller(), so, "REFUND_DEDUCTION", share, order.getCurrencyCode())
                );
            }
        }

        // Outbox notification to customer
        String payload = String.format("{\"refundId\":\"%s\",\"orderId\":\"%s\",\"amount\":%s,\"status\":\"APPROVED\"}",
                req.getPublicId(), order.getPublicId(), req.getRequestedAmount());
        outboxRepository.save(
                CommunicationOutbox.create("RefundApproved", order.getUser().getEmail(),
                        "Refund Approved for Order " + order.getPublicId(), payload)
        );

        return toView(req);
    }

    @Transactional
    public RefundView rejectRefund(String refundPublicId, Long approverUserId) {
        UUID refundUuid = UUID.fromString(refundPublicId);
        RefundRequest req = refundRequestRepository.findByPublicId(refundUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Refund request not found."));

        if (!"PENDING".equals(req.getStatus())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Refund request is already processed.");
        }

        User approver = userRepository.findById(approverUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Approver not found."));

        req.reject(approver);
        refundRequestRepository.save(req);

        // Outbox notification to customer
        ParentOrder order = req.getParentOrder();
        String payload = String.format("{\"refundId\":\"%s\",\"orderId\":\"%s\",\"status\":\"REJECTED\"}",
                req.getPublicId(), order.getPublicId());
        outboxRepository.save(
                CommunicationOutbox.create("RefundRejected", order.getUser().getEmail(),
                        "Refund Request Update for Order " + order.getPublicId(), payload)
        );

        return toView(req);
    }

    private RefundView toView(RefundRequest r) {
        return new RefundView(
                r.getPublicId().toString(),
                r.getParentOrder().getPublicId().toString(),
                r.getSellerOrder() != null ? r.getSellerOrder().getPublicId().toString() : null,
                r.getRequestedAmount(),
                r.getReason(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }
}
