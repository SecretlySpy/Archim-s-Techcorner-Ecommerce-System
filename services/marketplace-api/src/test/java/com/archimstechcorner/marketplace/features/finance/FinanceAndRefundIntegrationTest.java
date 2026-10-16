package com.archimstechcorner.marketplace.features.finance;

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
import com.archimstechcorner.marketplace.features.finance.application.RefundService;
import com.archimstechcorner.marketplace.features.finance.application.SellerBalanceService;
import com.archimstechcorner.marketplace.features.finance.domain.repository.RefundRequestRepository;
import com.archimstechcorner.marketplace.features.finance.domain.repository.SellerLedgerEntryRepository;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.notifications.domain.model.CommunicationOutbox;
import com.archimstechcorner.marketplace.features.notifications.domain.repository.CommunicationOutboxRepository;
import com.archimstechcorner.marketplace.features.orders.application.CheckoutQuoteService;
import com.archimstechcorner.marketplace.features.orders.application.OrderCheckoutService;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
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
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinanceAndRefundIntegrationTest {

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

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

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
    @Autowired private RefundService refundService;
    @Autowired private RefundRequestRepository refundRequestRepository;
    @Autowired private SellerLedgerEntryRepository ledgerEntryRepository;
    @Autowired private SellerBalanceService sellerBalanceService;
    @Autowired private CommunicationOutboxRepository outboxRepository;

    private User testCustomer;
    private User testSellerUser;
    private User adminUser;
    private User supportUser;
    private Seller testSeller;
    private Warehouse testWarehouse;
    private ProductVariant testVariant;
    private CustomerAddress testAddress;
    private ParentOrder testOrder;
    private SellerOrder testSellerOrder;

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

        adminUser = User.create();
        adminUser.setPublicId(UUID.randomUUID());
        adminUser.setEmailNormalized("admin_" + uid + "@example.com");
        adminUser.setEmailDisplay("admin_" + uid + "@example.com");
        adminUser.setPasswordHash("hash123");
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);

        supportUser = User.create();
        supportUser.setPublicId(UUID.randomUUID());
        supportUser.setEmailNormalized("supp_" + uid + "@example.com");
        supportUser.setEmailDisplay("supp_" + uid + "@example.com");
        supportUser.setPasswordHash("hash123");
        supportUser.setStatus(UserStatus.ACTIVE);
        supportUser = userRepository.save(supportUser);

        testSeller = Seller.create("Finance Seller " + uid, "TAX-" + uid);
        testSeller.activate();
        testSeller.addStaff(SellerStaff.create(testSeller, testSellerUser, SellerRole.OWNER));
        testSeller = sellerRepository.save(testSeller);

        Store store = storeRepository.save(Store.create(testSeller, "Finance Store " + uid, "Description"));
        testWarehouse = warehouseRepository.save(Warehouse.create(store, "Finance WH", "456 Ortigas", "Pasig", "NCR", "PH", "1600"));

        Category category = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.create(null, "Finance Cat " + uid, "fincat-" + uid, "Desc"))
        );

        Product product = productService.createProductAggregate(
                testSeller.getPublicId(),
                category.getId(),
                null,
                "Finance Laptop " + uid,
                "laptop-" + uid,
                "Laptop for finance testing",
                List.of(new ProductService.VariantInput("SKU-FIN-" + uid, "16GB", new BigDecimal("10000.00"), 10, "{}")),
                testSellerUser.getId()
        );
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);
        testVariant = product.getVariants().get(0);

        inventoryService.setStock(testWarehouse.getPublicId(), testVariant.getPublicId().toString(), 10, 2, testSellerUser.getId());

        testAddress = CustomerAddress.create();
        testAddress.setUserId(testCustomer.getId());
        testAddress.setRecipientName("Customer " + uid);
        testAddress.setPhone("09170000001");
        testAddress.setLine1("123 Main St");
        testAddress.setCity("Pasig");
        testAddress.setState("NCR");
        testAddress.setPostalCode("1600");
        testAddress.setCountry("PH");
        testAddress.setAddressType("SHIPPING");
        testAddress.setDefault(true);
        testAddress = addressRepository.save(testAddress);

        // Place and capture order
        cartService.addToCart(testCustomer.getId(), testVariant.getPublicId().toString(), 1);
        var quote = quoteService.generateQuote(testCustomer.getId(), testAddress.getPublicId().toString(), "standard");
        var checkoutResult = checkoutService.checkout(testCustomer.getId(), quote.quoteId(), "idem-fin-" + uid, "MOCK_HOSTED", "http://localhost:3000");

        reconciliationService.processWebhook("mock",
                String.format("{\"orderPublicId\":\"%s\",\"status\":\"SUCCEEDED\"}", checkoutResult.orderId()),
                "valid_mock_signature");

        testOrder = parentOrderRepository.findByPublicId(UUID.fromString(checkoutResult.orderId())).orElseThrow();
        testSellerOrder = sellerOrderRepository.findByParentOrder_Id(testOrder.getId()).get(0);
    }

    @Test
    void testEndToEndRefundLifecycleAndSeparationOfDuties() throws Exception {
        // 1. Initial State: Order is PAID, Seller has ledger entry from capture (9000 PHP net of 10% commission on 10000)
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        SellerBalanceService.SellerBalanceSummary initialBalance = sellerBalanceService.getSellerBalance(
                testSeller.getPublicId(), testSellerUser.getId(), false
        );
        assertThat(initialBalance.currentBalance()).isEqualByComparingTo(new BigDecimal("9000.00"));
        assertThat(initialBalance.totalCredited()).isEqualByComparingTo(new BigDecimal("9000.00"));
        assertThat(initialBalance.totalRefunded()).isEqualByComparingTo(BigDecimal.ZERO);

        // 2. Customer submits refund request for 2000.00 PHP
        RefundService.RefundView refundReq = refundService.requestRefund(
                testOrder.getPublicId().toString(),
                new BigDecimal("2000.00"),
                "Defective display port",
                testSellerOrder.getPublicId().toString(),
                testCustomer.getId()
        );
        assertThat(refundReq.status()).isEqualTo("PENDING");
        assertThat(refundReq.requestedAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));

        // Outbox recorded event
        List<CommunicationOutbox> outbox = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(outbox).anyMatch(o -> "RefundRequested".equals(o.getEventType()));

        // 3. Separation of Duties (G30, G31): Support agent CANNOT approve refund (lacks refund.approve.standard)
        AuthenticatedUser supportPrincipal = new AuthenticatedUser(
                supportUser.getId(), supportUser.getPublicId(), supportUser.getEmailDisplay(), 1,
                List.of("ROLE_SUPPORT", "order.read.any")
        );

        mvc.perform(post("/api/v1/admin/refunds/" + refundReq.id() + "/approve")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                supportPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPPORT"), new SimpleGrantedAuthority("order.read.any"))
                        ))))
                .andExpect(status().isForbidden());

        // 4. Authorized Admin / Finance approves refund
        AuthenticatedUser adminPrincipal = new AuthenticatedUser(
                adminUser.getId(), adminUser.getPublicId(), adminUser.getEmailDisplay(), 1,
                List.of("ROLE_ADMIN", "refund.approve.standard")
        );

        mvc.perform(post("/api/v1/admin/refunds/" + refundReq.id() + "/approve")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("refund.approve.standard"))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 5. Verify Seller Ledger and Balance updated
        SellerBalanceService.SellerBalanceSummary updatedBalance = sellerBalanceService.getSellerBalance(
                testSeller.getPublicId(), testSellerUser.getId(), false
        );
        // 9000.00 credited - 2000.00 refunded = 7000.00 net balance
        assertThat(updatedBalance.currentBalance()).isEqualByComparingTo(new BigDecimal("7000.00"));
        assertThat(updatedBalance.totalRefunded()).isEqualByComparingTo(new BigDecimal("2000.00"));

        // 6. Verify Seller can query balance and ledger via REST API
        AuthenticatedUser sellerPrincipal = new AuthenticatedUser(
                testSellerUser.getId(), testSellerUser.getPublicId(), testSellerUser.getEmailDisplay(), 1,
                List.of("ROLE_SELLER")
        );

        mvc.perform(get("/api/v1/sellers/" + testSeller.getPublicId() + "/balance")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                sellerPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_SELLER"))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(7000.00))
                .andExpect(jsonPath("$.totalRefunded").value(2000.00));

        mvc.perform(get("/api/v1/sellers/" + testSeller.getPublicId() + "/ledger")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                sellerPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_SELLER"))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)); // ORDER_CAPTURE + REFUND_DEDUCTION

        // 7. Idempotency Conflict: re-approving already processed refund throws conflict
        mvc.perform(post("/api/v1/admin/refunds/" + refundReq.id() + "/approve")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("refund.approve.standard"))
                        ))))
                .andExpect(status().isConflict());
    }
}
