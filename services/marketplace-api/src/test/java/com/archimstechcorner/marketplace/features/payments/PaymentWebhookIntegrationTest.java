package com.archimstechcorner.marketplace.features.payments;

import com.archimstechcorner.marketplace.features.cart.application.CartService;
import com.archimstechcorner.marketplace.features.catalog.application.ProductService;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.CategoryRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductRepository;
import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerAddressRepository;
import com.archimstechcorner.marketplace.features.finance.domain.model.SellerLedgerEntry;
import com.archimstechcorner.marketplace.features.finance.domain.repository.SellerLedgerEntryRepository;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.inventory.domain.model.Inventory;
import com.archimstechcorner.marketplace.features.inventory.domain.model.InventoryReservation;
import com.archimstechcorner.marketplace.features.inventory.domain.model.ReservationStatus;
import com.archimstechcorner.marketplace.features.inventory.domain.repository.InventoryRepository;
import com.archimstechcorner.marketplace.features.inventory.domain.repository.InventoryReservationRepository;
import com.archimstechcorner.marketplace.features.notifications.domain.model.CommunicationOutbox;
import com.archimstechcorner.marketplace.features.notifications.domain.repository.CommunicationOutboxRepository;
import com.archimstechcorner.marketplace.features.orders.application.CheckoutQuoteService;
import com.archimstechcorner.marketplace.features.orders.application.OrderCheckoutService;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.repository.ParentOrderRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.SellerOrderRepository;
import com.archimstechcorner.marketplace.features.payments.application.PaymentReconciliationService;
import com.archimstechcorner.marketplace.features.payments.domain.model.OrderPayment;
import com.archimstechcorner.marketplace.features.payments.domain.model.PaymentStatus;
import com.archimstechcorner.marketplace.features.payments.domain.repository.OrderPaymentRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.model.*;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.StoreRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.WarehouseRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PaymentWebhookIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final RedisContainer REDIS = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("7-alpine"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("auth.jwt-secret", () -> "integration_test_secret_key_long_enough_1234567890");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private CustomerAddressRepository addressRepository;
    @Autowired private CartService cartService;
    @Autowired private CheckoutQuoteService quoteService;
    @Autowired private OrderCheckoutService checkoutService;
    @Autowired private PaymentReconciliationService reconciliationService;
    @Autowired private OrderPaymentRepository paymentRepository;
    @Autowired private ParentOrderRepository parentOrderRepository;
    @Autowired private SellerOrderRepository sellerOrderRepository;
    @Autowired private InventoryReservationRepository reservationRepository;
    @Autowired private SellerLedgerEntryRepository sellerLedgerEntryRepository;
    @Autowired private CommunicationOutboxRepository outboxRepository;

    private User testCustomer;
    private User testSellerUser;
    private Seller testSeller;
    private Warehouse testWarehouse;
    private ProductVariant testVariant;
    private CustomerAddress testAddress;

    @BeforeEach
    void setup() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        testCustomer = User.create();
        testCustomer.setPublicId(UUID.randomUUID());
        testCustomer.setEmailNormalized("cust_" + uid + "@example.com");
        testCustomer.setEmailDisplay("cust_" + uid + "@example.com");
        testCustomer.setPasswordHash("hash123");
        testCustomer.setStatus(UserStatus.ACTIVE);
        testCustomer = userRepository.save(testCustomer);

        testSellerUser = User.create();
        testSellerUser.setPublicId(UUID.randomUUID());
        testSellerUser.setEmailNormalized("sell_" + uid + "@example.com");
        testSellerUser.setEmailDisplay("sell_" + uid + "@example.com");
        testSellerUser.setPasswordHash("hash123");
        testSellerUser.setStatus(UserStatus.ACTIVE);
        testSellerUser = userRepository.save(testSellerUser);

        testSeller = Seller.create("Tech Seller " + uid, "TAX-" + uid);
        testSeller.activate();
        testSeller.addStaff(SellerStaff.create(testSeller, testSellerUser, SellerRole.OWNER));
        testSeller = sellerRepository.save(testSeller);

        Store store = storeRepository.save(Store.create(testSeller, "Main Store " + uid, "Description"));
        testWarehouse = warehouseRepository.save(Warehouse.create(store, "Makati WH", "123 Ayala", "Makati", "NCR", "PH", "1200"));

        Category category = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.create(null, "Cat " + uid, "cat-" + uid, "Desc"))
        );

        Product product = productService.createProductAggregate(
                testSeller.getPublicId(),
                category.getId(),
                null,
                "Gaming Laptop " + uid,
                "laptop-" + uid,
                "High performance laptop",
                List.of(new ProductService.VariantInput("SKU-" + uid, "16GB RAM", new BigDecimal("1200.00"), 10, "{}")),
                testSellerUser.getId()
        );
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);
        testVariant = product.getVariants().get(0);

        inventoryService.setStock(testWarehouse.getPublicId(), testVariant.getPublicId().toString(), 10, 2, testSellerUser.getId());

        testAddress = CustomerAddress.create();
        testAddress.setUserId(testCustomer.getId());
        testAddress.setRecipientName("Cust " + uid);
        testAddress.setPhone("09170000001");
        testAddress.setLine1("123 Main St");
        testAddress.setCity("Makati");
        testAddress.setState("NCR");
        testAddress.setPostalCode("1200");
        testAddress.setCountry("PH");
        testAddress.setAddressType("SHIPPING");
        testAddress.setDefault(true);
        testAddress = addressRepository.save(testAddress);
    }

    @Test
    void testPaymentWebhook_CapturedSuccess_ConsumesStock_CreatesLedger_EmitsOutbox() {
        // 1. Setup Cart & Place Order via Checkout
        cartService.addToCart(testCustomer.getId(), testVariant.getPublicId().toString(), 2);
        var quote = quoteService.generateQuote(testCustomer.getId(), testAddress.getPublicId().toString(), "standard");

        String idempotencyKey = "webhook-test-key-" + UUID.randomUUID();
        var checkoutResult = checkoutService.checkout(testCustomer.getId(), quote.quoteId(), idempotencyKey, "MOCK_HOSTED", "http://localhost:3000");

        ParentOrder order = parentOrderRepository.findByPublicId(UUID.fromString(checkoutResult.orderId())).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        Inventory invBefore = inventoryRepository.findByWarehouse_IdAndVariant_Id(testWarehouse.getId(), testVariant.getId()).orElseThrow();
        assertThat(invBefore.getOnHandQuantity()).isEqualTo(10);
        assertThat(invBefore.getReservedQuantity()).isEqualTo(2);

        // 2. Invoke Webhook with SUCCEEDED status
        String webhookPayload = String.format("{\"orderPublicId\":\"%s\",\"status\":\"SUCCEEDED\",\"providerPaymentId\":\"pay_prov_123\"}",
                checkoutResult.orderId());

        var result = reconciliationService.processWebhook("mock", webhookPayload, "valid_mock_signature");
        assertThat(result.status()).isEqualTo("CAPTURED");

        // 3. Verify Payment Status & Order Status
        OrderPayment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_prov_123");

        ParentOrder orderAfter = parentOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.PAID);

        var sellerOrders = sellerOrderRepository.findByParentOrder_Id(order.getId());
        assertThat(sellerOrders).hasSize(1);
        assertThat(sellerOrders.get(0).getStatus()).isEqualTo(SellerOrderStatus.PAID);

        // 4. Verify Stock Consumed: onHand was 10 -> now 8; reserved was 2 -> now 0
        Inventory invAfter = inventoryRepository.findByWarehouse_IdAndVariant_Id(testWarehouse.getId(), testVariant.getId()).orElseThrow();
        assertThat(invAfter.getOnHandQuantity()).isEqualTo(8);
        assertThat(invAfter.getReservedQuantity()).isEqualTo(0);

        List<InventoryReservation> resList = reservationRepository.findByOrderId(order.getId());
        assertThat(resList).hasSize(1);
        assertThat(resList.get(0).getStatus()).isEqualTo(ReservationStatus.CONSUMED);

        // 5. Verify Seller Ledger Entry Created
        List<SellerLedgerEntry> ledgerEntries = sellerLedgerEntryRepository.findBySeller_IdOrderByCreatedAtDesc(testSeller.getId());
        assertThat(ledgerEntries).hasSize(1);
        assertThat(ledgerEntries.get(0).getTransactionType()).isEqualTo("ORDER_EARNING");
        // Subtotal = 2 * 1200 = 2400. 10% commission = 240. Net earning = 2160.00
        assertThat(ledgerEntries.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("2160.00"));

        // 6. Verify Communication Outbox Entries
        List<CommunicationOutbox> outbox = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(outbox).anyMatch(o -> o.getEventType().equals("OrderPaid") && o.getRecipient().equals(testCustomer.getEmail()));
        assertThat(outbox).anyMatch(o -> o.getEventType().equals("SellerOrderPaid"));
    }

    @Test
    void testPaymentWebhook_IdempotentDuplicateCall() {
        cartService.addToCart(testCustomer.getId(), testVariant.getPublicId().toString(), 1);
        var quote = quoteService.generateQuote(testCustomer.getId(), testAddress.getPublicId().toString(), "standard");
        var checkoutResult = checkoutService.checkout(testCustomer.getId(), quote.quoteId(), "idem-dup-" + UUID.randomUUID(), "MOCK_HOSTED", "http://localhost:3000");

        String webhookPayload = String.format("{\"orderPublicId\":\"%s\",\"status\":\"SUCCEEDED\"}", checkoutResult.orderId());

        // First call
        var res1 = reconciliationService.processWebhook("mock", webhookPayload, "valid_mock_signature");
        assertThat(res1.status()).isEqualTo("CAPTURED");

        // Second call with same payload -> idempotent return
        var res2 = reconciliationService.processWebhook("mock", webhookPayload, "valid_mock_signature");
        assertThat(res2.status()).isEqualTo("ALREADY_PROCESSED");

        // Ensure stock only decremented once
        Inventory inv = inventoryRepository.findByWarehouse_IdAndVariant_Id(testWarehouse.getId(), testVariant.getId()).orElseThrow();
        assertThat(inv.getOnHandQuantity()).isEqualTo(9); // 10 - 1 = 9
    }

    @Test
    void testPaymentWebhook_FailedPayment_ReleasesStockAndCancelsOrder() {
        cartService.addToCart(testCustomer.getId(), testVariant.getPublicId().toString(), 3);
        var quote = quoteService.generateQuote(testCustomer.getId(), testAddress.getPublicId().toString(), "standard");
        var checkoutResult = checkoutService.checkout(testCustomer.getId(), quote.quoteId(), "idem-fail-" + UUID.randomUUID(), "MOCK_HOSTED", "http://localhost:3000");

        ParentOrder order = parentOrderRepository.findByPublicId(UUID.fromString(checkoutResult.orderId())).orElseThrow();

        // Failed webhook
        String webhookPayload = String.format("{\"orderPublicId\":\"%s\",\"status\":\"FAILED\"}", checkoutResult.orderId());
        var res = reconciliationService.processWebhook("mock", webhookPayload, "valid_mock_signature");
        assertThat(res.status()).isEqualTo("FAILED");

        // Assert payment FAILED, order CANCELLED
        OrderPayment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        ParentOrder orderAfter = parentOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Assert reservations RELEASED, on-hand remains 10, reserved becomes 0
        Inventory inv = inventoryRepository.findByWarehouse_IdAndVariant_Id(testWarehouse.getId(), testVariant.getId()).orElseThrow();
        assertThat(inv.getOnHandQuantity()).isEqualTo(10);
        assertThat(inv.getReservedQuantity()).isEqualTo(0);

        List<InventoryReservation> resList = reservationRepository.findByOrderId(order.getId());
        assertThat(resList.get(0).getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void testPaymentWebhook_InvalidSignature_Rejected() {
        String payload = "{\"status\":\"SUCCEEDED\"}";
        assertThatThrownBy(() -> reconciliationService.processWebhook("mock", payload, "invalid_signature"))
                .isInstanceOf(ApiException.class);
    }
}
