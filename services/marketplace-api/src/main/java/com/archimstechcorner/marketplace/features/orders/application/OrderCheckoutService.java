package com.archimstechcorner.marketplace.features.orders.application;

import com.archimstechcorner.marketplace.features.cart.application.CartService;
import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerAddressRepository;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.orders.domain.model.CheckoutQuote;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderAddress;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderItem;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.orders.domain.repository.CheckoutQuoteRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.OrderAddressRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.ParentOrderRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.SellerOrderRepository;
import com.archimstechcorner.marketplace.features.payments.application.port.PaymentGateway;
import com.archimstechcorner.marketplace.features.payments.domain.model.OrderPayment;
import com.archimstechcorner.marketplace.features.payments.domain.repository.OrderPaymentRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderCheckoutService {

    private final CheckoutQuoteService quoteService;
    private final CheckoutQuoteRepository quoteRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final CustomerAddressRepository addressRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final SellerRepository sellerRepository;
    private final InventoryService inventoryService;
    private final OrderPaymentRepository paymentRepository;
    private final CartService cartService;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OrderCheckoutService(CheckoutQuoteService quoteService,
                                CheckoutQuoteRepository quoteRepository,
                                ParentOrderRepository parentOrderRepository,
                                SellerOrderRepository sellerOrderRepository,
                                CustomerAddressRepository addressRepository,
                                OrderAddressRepository orderAddressRepository,
                                SellerRepository sellerRepository,
                                InventoryService inventoryService,
                                OrderPaymentRepository paymentRepository,
                                CartService cartService,
                                PaymentGateway paymentGateway,
                                ObjectMapper objectMapper,
                                TransactionTemplate transactionTemplate) {
        this.quoteService = quoteService;
        this.quoteRepository = quoteRepository;
        this.parentOrderRepository = parentOrderRepository;
        this.sellerOrderRepository = sellerOrderRepository;
        this.addressRepository = addressRepository;
        this.orderAddressRepository = orderAddressRepository;
        this.sellerRepository = sellerRepository;
        this.inventoryService = inventoryService;
        this.paymentRepository = paymentRepository;
        this.cartService = cartService;
        this.paymentGateway = paymentGateway;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public record CheckoutExecutionResult(
            String orderId,
            String status,
            BigDecimal total,
            String redirectUrl
    ) {}

    public record CommittedOrderDetails(
            ParentOrder order,
            OrderPayment payment,
            boolean isReplay
    ) {}

    /**
     * Executes the complete checkout workflow:
     * 1. In a single MySQL transaction (T1) via TransactionTemplate, checks idempotency, locks inventory,
     *    reserves stock, creates parent order, seller orders, order items, address snapshot, payment attempt,
     *    and clears cart. Any error triggers automatic rollback.
     * 2. OUTSIDE database locks, calls the hosted payment gateway.
     */
    public CheckoutExecutionResult checkout(Long userId, String quotePublicId, String idempotencyKey,
                                            String paymentMethod, String baseUrl) {
        CommittedOrderDetails details = transactionTemplate.execute(status ->
                performDurableCheckout(userId, quotePublicId, idempotencyKey, paymentMethod)
        );

        ParentOrder order = details.order();
        OrderPayment payment = details.payment();

        String appBase = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "http://localhost:3000";
        String successUrl = appBase + "/checkout?success=true";
        String cancelUrl = appBase + "/checkout?cancel=true";

        String redirectUrl = null;
        if (payment.getProviderPaymentId() == null) {
            PaymentGateway.HostedSession session = paymentGateway.createHostedSession(
                    order.getPublicId().toString(),
                    order.getTotalAmount(),
                    order.getCurrencyCode(),
                    successUrl,
                    cancelUrl
            );
            updatePaymentProviderReference(payment.getId(), session.sessionId());
            redirectUrl = session.redirectUrl();
        }

        return new CheckoutExecutionResult(
                order.getPublicId().toString(),
                order.getStatus().name(),
                order.getTotalAmount(),
                redirectUrl
        );
    }

    /**
     * G01/G03/G13: Transaction T1 — Atomic reservation, order snapshots, and quote consumption.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommittedOrderDetails performDurableCheckout(Long userId, String quotePublicId,
                                                        String idempotencyKey, String paymentMethod) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<ParentOrder> existing = parentOrderRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existing.isPresent()) {
                ParentOrder order = existing.get();
                if (!order.getUser().getId().equals(userId)) {
                    throw new ApiException(ErrorCode.FORBIDDEN, "Idempotency key belongs to another user.");
                }
                OrderPayment pay = paymentRepository.findByOrderId(order.getId()).orElse(null);
                return new CommittedOrderDetails(order, pay, true);
            }
        }

        CheckoutQuote quote = quoteService.requireActiveQuote(quotePublicId, userId);

        CustomerAddress customerAddress = addressRepository.findById(quote.getShippingAddressId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipping address not found."));

        List<CheckoutQuoteService.QuoteItemSnapshot> items;
        try {
            items = objectMapper.readValue(quote.getItemsJson(),
                    new TypeReference<List<CheckoutQuoteService.QuoteItemSnapshot>>() {});
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to deserialize quote items.");
        }

        // Sort deterministically by variantId to guarantee no deadlocks between concurrent checkouts
        List<CheckoutQuoteService.QuoteItemSnapshot> sortedItems = items.stream()
                .sorted(Comparator.comparing(CheckoutQuoteService.QuoteItemSnapshot::variantId))
                .toList();

        // 1. Create ParentOrder
        ParentOrder parentOrder = ParentOrder.create(
                quote.getUser(),
                quote.getTotalAmount(),
                quote.getCurrencyCode(),
                (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null
        );
        parentOrder = parentOrderRepository.save(parentOrder);

        // 2. Lock inventory & create reservations (fails transaction immediately if stock is insufficient)
        for (CheckoutQuoteService.QuoteItemSnapshot item : sortedItems) {
            inventoryService.reserveStockForOrder(
                    item.variantId(),
                    item.quantity(),
                    parentOrder.getId(),
                    quote.getExpiresAt()
            );
        }

        // 3. Persist OrderAddress snapshot
        OrderAddress orderAddress = OrderAddress.create(
                parentOrder,
                customerAddress.getRecipientName(),
                customerAddress.getPhone(),
                customerAddress.getLine1(),
                customerAddress.getLine2(),
                customerAddress.getCity(),
                customerAddress.getState(),
                customerAddress.getPostalCode(),
                customerAddress.getCountry()
        );
        orderAddress = orderAddressRepository.save(orderAddress);
        parentOrder.setShippingAddress(orderAddress);

        // 4. Partition items by seller into SellerOrders
        Map<Long, List<CheckoutQuoteService.QuoteItemSnapshot>> bySeller = sortedItems.stream()
                .collect(Collectors.groupingBy(CheckoutQuoteService.QuoteItemSnapshot::sellerId));

        for (Map.Entry<Long, List<CheckoutQuoteService.QuoteItemSnapshot>> entry : bySeller.entrySet()) {
            Long sellerId = entry.getKey();
            List<CheckoutQuoteService.QuoteItemSnapshot> sellerItems = entry.getValue();

            Seller seller = sellerRepository.findById(sellerId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Seller not found for order partition."));

            BigDecimal sellerSubtotal = sellerItems.stream()
                    .map(CheckoutQuoteService.QuoteItemSnapshot::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            SellerOrder sellerOrder = SellerOrder.create(parentOrder, seller, sellerSubtotal);
            for (CheckoutQuoteService.QuoteItemSnapshot si : sellerItems) {
                OrderItem orderItem = OrderItem.create(
                        sellerOrder,
                        si.variantId(),
                        si.productName(),
                        si.variantName(),
                        si.sku(),
                        si.unitPrice(),
                        si.quantity()
                );
                sellerOrder.addItem(orderItem);
            }
            parentOrder.addSellerOrder(sellerOrder);
            sellerOrderRepository.save(sellerOrder);
        }

        // 5. Create OrderPayment
        String provider = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod.trim() : "MOCK_HOSTED";
        String payKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ?
                idempotencyKey.trim() + "_pay" : "pay_" + parentOrder.getPublicId().toString();

        OrderPayment payment = OrderPayment.create(
                parentOrder,
                payKey,
                provider,
                parentOrder.getTotalAmount(),
                parentOrder.getCurrencyCode()
        );
        payment = paymentRepository.save(payment);

        // 6. Mark quote as consumed
        quote.markUsed();
        quoteRepository.save(quote);

        // 7. Clear checked-out items from active cart
        cartService.clearCart(userId);

        parentOrder = parentOrderRepository.save(parentOrder);
        return new CommittedOrderDetails(parentOrder, payment, false);
    }

    @Transactional
    public void updatePaymentProviderReference(Long paymentId, String providerSessionId) {
        paymentRepository.findById(paymentId).ifPresent(p -> {
            p.setProviderPaymentId(providerSessionId);
            paymentRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public ParentOrder getOrder(String orderPublicId, Long userId) {
        java.util.UUID orderUuid;
        try {
            orderUuid = java.util.UUID.fromString(orderPublicId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Order not found.");
        }
        ParentOrder order = parentOrderRepository.findByPublicId(orderUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found."));

        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this order.");
        }
        if (order.getShippingAddress() == null) {
            orderAddressRepository.findByOrderId(order.getId()).ifPresent(order::setShippingAddress);
        }
        order.getSellerOrders().forEach(so -> so.getItems().size());
        return order;
    }

    @Transactional(readOnly = true)
    public List<ParentOrder> listUserOrders(Long userId) {
        List<ParentOrder> orders = parentOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        orders.forEach(o -> {
            if (o.getShippingAddress() == null) {
                orderAddressRepository.findByOrderId(o.getId()).ifPresent(o::setShippingAddress);
            }
            o.getSellerOrders().forEach(so -> so.getItems().size());
        });
        return orders;
    }
}
