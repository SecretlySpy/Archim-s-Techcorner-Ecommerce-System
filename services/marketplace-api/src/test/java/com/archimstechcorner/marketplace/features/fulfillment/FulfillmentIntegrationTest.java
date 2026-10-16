package com.archimstechcorner.marketplace.features.fulfillment;

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
import com.archimstechcorner.marketplace.features.fulfillment.application.FulfillmentService;
import com.archimstechcorner.marketplace.features.fulfillment.domain.model.Shipment;
import com.archimstechcorner.marketplace.features.fulfillment.domain.repository.ShipmentRepository;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.notifications.application.OutboxDispatcherService;
import com.archimstechcorner.marketplace.features.notifications.domain.model.CommunicationOutbox;
import com.archimstechcorner.marketplace.features.notifications.domain.repository.CommunicationOutboxRepository;
import com.archimstechcorner.marketplace.features.orders.application.CheckoutQuoteService;
import com.archimstechcorner.marketplace.features.orders.application.OrderCheckoutService;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.repository.ParentOrderRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.SellerOrderRepository;
import com.archimstechcorner.marketplace.features.payments.application.PaymentReconciliationService;
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
class FulfillmentIntegrationTest {

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
    @Autowired private InventoryService inventoryService;
    @Autowired private CustomerAddressRepository addressRepository;
    @Autowired private CartService cartService;
    @Autowired private CheckoutQuoteService quoteService;
    @Autowired private OrderCheckoutService checkoutService;
    @Autowired private PaymentReconciliationService reconciliationService;
    @Autowired private ParentOrderRepository parentOrderRepository;
    @Autowired private SellerOrderRepository sellerOrderRepository;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private OutboxDispatcherService outboxDispatcherService;
    @Autowired private CommunicationOutboxRepository outboxRepository;

    private User testCustomer;
    private User testSellerUser;
    private User otherSellerUser;
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

        otherSellerUser = User.create();
        otherSellerUser.setPublicId(UUID.randomUUID());
        otherSellerUser.setEmailNormalized("other_" + uid + "@example.com");
        otherSellerUser.setEmailDisplay("other_" + uid + "@example.com");
        otherSellerUser.setPasswordHash("hash123");
        otherSellerUser.setStatus(UserStatus.ACTIVE);
        otherSellerUser = userRepository.save(otherSellerUser);

        testSeller = Seller.create("Fulfill Seller " + uid, "TAX-" + uid);
        testSeller.activate();
        testSeller.addStaff(SellerStaff.create(testSeller, testSellerUser, SellerRole.OWNER));
        testSeller = sellerRepository.save(testSeller);

        Store store = storeRepository.save(Store.create(testSeller, "Fulfill Store " + uid, "Description"));
        testWarehouse = warehouseRepository.save(Warehouse.create(store, "Pasig WH", "456 Ortigas", "Pasig", "NCR", "PH", "1600"));

        Category category = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.create(null, "Electronics " + uid, "elect-" + uid, "Desc"))
        );

        Product product = productService.createProductAggregate(
                testSeller.getPublicId(),
                category.getId(),
                null,
                "Monitor 4K " + uid,
                "monitor-" + uid,
                "4K Gaming Monitor",
                List.of(new ProductService.VariantInput("SKU-MON-" + uid, "32 Inch", new BigDecimal("15000.00"), 10, "{}")),
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
        testAddress.setCity("Pasig");
        testAddress.setState("NCR");
        testAddress.setPostalCode("1600");
        testAddress.setCountry("PH");
        testAddress.setAddressType("SHIPPING");
        testAddress.setDefault(true);
        testAddress = addressRepository.save(testAddress);
    }

    @Test
    void testShipmentCreation_Success_UpdatesStatuses_AndEmitsOutbox() {
        // Place and capture order
        cartService.addToCart(testCustomer.getId(), testVariant.getPublicId().toString(), 1);
        var quote = quoteService.generateQuote(testCustomer.getId(), testAddress.getPublicId().toString(), "standard");
        var checkoutResult = checkoutService.checkout(testCustomer.getId(), quote.quoteId(), "idem-ship-" + UUID.randomUUID(), "MOCK_HOSTED", "http://localhost:3000");

        reconciliationService.processWebhook("mock",
                String.format("{\"orderPublicId\":\"%s\",\"status\":\"SUCCEEDED\"}", checkoutResult.orderId()),
                "valid_mock_signature");

        ParentOrder order = parentOrderRepository.findByPublicId(UUID.fromString(checkoutResult.orderId())).orElseThrow();
        SellerOrder sellerOrder = sellerOrderRepository.findByParentOrder_Id(order.getId()).get(0);
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PAID);

        // Dispatch shipment by seller
        String courier = "J_AND_T";
        String tracking = "JT-" + UUID.randomUUID().toString().substring(0, 8);
        var shipmentView = fulfillmentService.createShipment(sellerOrder.getPublicId().toString(), courier, tracking, testSellerUser.getId());

        assertThat(shipmentView.id()).isNotBlank();
        assertThat(shipmentView.courierCode()).isEqualTo(courier);
        assertThat(shipmentView.trackingNumber()).isEqualTo(tracking);
        assertThat(shipmentView.status()).isEqualTo("DISPATCHED");

        // Verify seller order status updated to SHIPPED
        SellerOrder sellerOrderAfter = sellerOrderRepository.findById(sellerOrder.getId()).orElseThrow();
        assertThat(sellerOrderAfter.getStatus()).isEqualTo(SellerOrderStatus.SHIPPED);

        // Verify parent order status updated to SHIPPED
        ParentOrder parentAfter = parentOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(parentAfter.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        // Verify Customer tracking view
        var trackingList = fulfillmentService.getParentOrderShipments(order.getPublicId().toString(), testCustomer.getId());
        assertThat(trackingList).hasSize(1);
        assertThat(trackingList.get(0).trackingNumber()).isEqualTo(tracking);

        // Verify Outbox Notification emitted
        List<CommunicationOutbox> outbox = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(outbox).anyMatch(o -> o.getEventType().equals("ShipmentDispatched") && o.getRecipient().equals(testCustomer.getEmail()));

        // Test outbox dispatcher
        int dispatched = outboxDispatcherService.dispatchPendingNotifications();
        assertThat(dispatched).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testShipmentCreation_UnpaidOrderRejected() {
        // Place order without payment (remains PENDING_PAYMENT)
        cartService.addToCart(testCustomer.getId(), testVariant.getPublicId().toString(), 1);
        var quote = quoteService.generateQuote(testCustomer.getId(), testAddress.getPublicId().toString(), "standard");
        var checkoutResult = checkoutService.checkout(testCustomer.getId(), quote.quoteId(), "idem-unpaid-" + UUID.randomUUID(), "MOCK_HOSTED", "http://localhost:3000");

        ParentOrder order = parentOrderRepository.findByPublicId(UUID.fromString(checkoutResult.orderId())).orElseThrow();
        SellerOrder sellerOrder = sellerOrderRepository.findByParentOrder_Id(order.getId()).get(0);

        assertThatThrownBy(() -> fulfillmentService.createShipment(sellerOrder.getPublicId().toString(), "J_AND_T", "TRACK-999", testSellerUser.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void testShipmentCreation_CrossSellerForbidden() {
        // Place and pay order
        cartService.addToCart(testCustomer.getId(), testVariant.getPublicId().toString(), 1);
        var quote = quoteService.generateQuote(testCustomer.getId(), testAddress.getPublicId().toString(), "standard");
        var checkoutResult = checkoutService.checkout(testCustomer.getId(), quote.quoteId(), "idem-cross-" + UUID.randomUUID(), "MOCK_HOSTED", "http://localhost:3000");

        reconciliationService.processWebhook("mock",
                String.format("{\"orderPublicId\":\"%s\",\"status\":\"SUCCEEDED\"}", checkoutResult.orderId()),
                "valid_mock_signature");

        ParentOrder order = parentOrderRepository.findByPublicId(UUID.fromString(checkoutResult.orderId())).orElseThrow();
        SellerOrder sellerOrder = sellerOrderRepository.findByParentOrder_Id(order.getId()).get(0);

        // otherSellerUser attempts to ship
        assertThatThrownBy(() -> fulfillmentService.createShipment(sellerOrder.getPublicId().toString(), "J_AND_T", "TRACK-CROSS", otherSellerUser.getId()))
                .isInstanceOf(ApiException.class);
    }
}
