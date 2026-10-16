package com.archimstechcorner.marketplace.features.orders;

import com.archimstechcorner.marketplace.features.cart.application.CartService;
import com.archimstechcorner.marketplace.features.catalog.application.ProductService;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.CategoryRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductVariantRepository;
import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerAddressRepository;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.inventory.domain.model.Inventory;
import com.archimstechcorner.marketplace.features.inventory.domain.model.InventoryReservation;
import com.archimstechcorner.marketplace.features.inventory.domain.model.ReservationStatus;
import com.archimstechcorner.marketplace.features.inventory.domain.repository.InventoryRepository;
import com.archimstechcorner.marketplace.features.inventory.domain.repository.InventoryReservationRepository;
import com.archimstechcorner.marketplace.features.orders.application.CheckoutQuoteService;
import com.archimstechcorner.marketplace.features.orders.application.OrderCheckoutService;
import com.archimstechcorner.marketplace.features.orders.domain.model.OrderStatus;
import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.orders.domain.repository.ParentOrderRepository;
import com.archimstechcorner.marketplace.features.orders.domain.repository.SellerOrderRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.model.*;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.StoreRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.WarehouseRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
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
class OrderAndCheckoutIntegrationTest {

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

    @Autowired
    private CheckoutQuoteService quoteService;

    @Autowired
    private OrderCheckoutService checkoutService;

    @Autowired
    private ParentOrderRepository parentOrderRepository;

    @Autowired
    private SellerOrderRepository sellerOrderRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    private User customer;
    private User sellerUser;
    private Seller seller;
    private Store store;
    private Warehouse warehouse;
    private Category category;
    private Product product;
    private ProductVariant variant;
    private CustomerAddress address;

    @BeforeEach
    void setup() {
        String runSuffix = UUID.randomUUID().toString().substring(0, 8);

        customer = User.create();
        customer.setPublicId(UUID.randomUUID());
        customer.setEmailNormalized("cust_" + runSuffix + "@example.com");
        customer.setEmailDisplay("cust_" + runSuffix + "@example.com");
        customer.setPasswordHash("hash123");
        customer.setStatus(UserStatus.ACTIVE);
        customer = userRepository.save(customer);

        sellerUser = User.create();
        sellerUser.setPublicId(UUID.randomUUID());
        sellerUser.setEmailNormalized("sell_" + runSuffix + "@example.com");
        sellerUser.setEmailDisplay("sell_" + runSuffix + "@example.com");
        sellerUser.setPasswordHash("hash123");
        sellerUser.setStatus(UserStatus.ACTIVE);
        sellerUser = userRepository.save(sellerUser);

        seller = Seller.create("Tech Seller " + runSuffix, "TAX-" + runSuffix);
        seller.activate();
        seller.addStaff(SellerStaff.create(seller, sellerUser, SellerRole.OWNER));
        seller = sellerRepository.save(seller);

        store = storeRepository.save(Store.create(seller, "Main Tech Store", "Store description"));
        warehouse = warehouseRepository.save(Warehouse.create(store, "Makati WH", "123 Ayala", "Makati", "NCR", "PH", "1200"));

        category = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.create(null, "Laptops " + runSuffix, "laptops-" + runSuffix, "Laptops description"))
        );

        product = productService.createProductAggregate(
                seller.getPublicId(),
                category.getId(),
                null,
                "Gaming Laptop RTX",
                "gaming-laptop-" + runSuffix,
                "Powerful gaming laptop",
                List.of(new ProductService.VariantInput("SKU-LAP-" + runSuffix, "16GB RAM", new BigDecimal("54999.00"), 15, "{}")),
                sellerUser.getId()
        );
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        variant = product.getVariants().get(0);

        // Populate stock in warehouse
        inventoryService.setStock(warehouse.getPublicId(), variant.getPublicId().toString(), 15, 2, sellerUser.getId());

        // Setup customer address
        address = CustomerAddress.create();
        address.setUserId(customer.getId());
        address.setRecipientName("Juan Dela Cruz");
        address.setPhone("09171234567");
        address.setLine1("Block 1 Lot 2 Sunset Blvd");
        address.setCity("Quezon City");
        address.setState("NCR");
        address.setPostalCode("1100");
        address.setCountry("PH");
        address.setDefault(true);
        address.setAddressType("SHIPPING");
        address = addressRepository.save(address);

        // Add 2 units of laptop to cart
        cartService.addToCart(customer.getId(), variant.getPublicId().toString(), 2);
    }

    @Test
    void testCreateQuote_Success() {
        CheckoutQuoteService.QuoteResult quote = quoteService.generateQuote(
                customer.getId(),
                address.getPublicId().toString(),
                "standard"
        );

        assertThat(quote.quoteId()).isNotBlank();
        assertThat(quote.currency()).isEqualTo("PHP");
        assertThat(quote.subtotal()).isEqualByComparingTo("109998.00"); // 54999 * 2
        assertThat(quote.shippingFee()).isEqualByComparingTo("150.00");
        assertThat(quote.totalAmount()).isEqualByComparingTo("110148.00");
        assertThat(quote.items()).hasSize(1);
        assertThat(quote.items().get(0).sku()).isEqualTo(variant.getSku());
    }

    @Test
    void testCheckout_AtomicReservationAndOrderCommit_Success() {
        CheckoutQuoteService.QuoteResult quote = quoteService.generateQuote(
                customer.getId(),
                address.getPublicId().toString(),
                "standard"
        );

        String idempotencyKey = "chk_idem_" + UUID.randomUUID();

        OrderCheckoutService.CheckoutExecutionResult result = checkoutService.checkout(
                customer.getId(),
                quote.quoteId(),
                idempotencyKey,
                "MOCK_HOSTED",
                "http://localhost:3000"
        );

        assertThat(result.orderId()).isNotBlank();
        assertThat(result.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.total()).isEqualByComparingTo("110148.00");
        assertThat(result.redirectUrl()).contains("mock_sess_");

        // Verify parent order in DB
        ParentOrder parentOrder = checkoutService.getOrder(result.orderId(), customer.getId());
        assertThat(parentOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(parentOrder.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(parentOrder.getShippingAddress()).isNotNull();
        assertThat(parentOrder.getShippingAddress().getRecipientName()).isEqualTo("Juan Dela Cruz");

        // Verify seller orders
        assertThat(parentOrder.getSellerOrders()).hasSize(1);
        SellerOrder sellerOrder = parentOrder.getSellerOrders().get(0);
        assertThat(sellerOrder.getItems()).hasSize(1);
        assertThat(sellerOrder.getItems().get(0).getSkuSnapshot()).isEqualTo(variant.getSku());
        assertThat(sellerOrder.getItems().get(0).getQuantity()).isEqualTo(2);

        // Verify inventory reservation in DB
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(parentOrder.getId());
        assertThat(reservations).hasSize(1);
        assertThat(reservations.get(0).getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservations.get(0).getQuantity()).isEqualTo(2);

        // Verify inventory bucket reserved quantity
        Inventory inv = inventoryRepository.findById(reservations.get(0).getInventory().getId()).orElseThrow();
        assertThat(inv.getReservedQuantity()).isEqualTo(2);
        assertThat(inv.getOnHandQuantity()).isEqualTo(15);

        // Verify cart cleared
        assertThat(cartService.getCart(customer.getId()).totalItems()).isEqualTo(0);
    }

    @Test
    void testCheckout_IdempotencyReplay() {
        CheckoutQuoteService.QuoteResult quote = quoteService.generateQuote(
                customer.getId(),
                address.getPublicId().toString(),
                "standard"
        );

        String idempotencyKey = "chk_replay_" + UUID.randomUUID();

        OrderCheckoutService.CheckoutExecutionResult first = checkoutService.checkout(
                customer.getId(),
                quote.quoteId(),
                idempotencyKey,
                "MOCK_HOSTED",
                "http://localhost:3000"
        );

        OrderCheckoutService.CheckoutExecutionResult second = checkoutService.checkout(
                customer.getId(),
                quote.quoteId(),
                idempotencyKey,
                "MOCK_HOSTED",
                "http://localhost:3000"
        );

        // Same order returned
        assertThat(first.orderId()).isEqualTo(second.orderId());
        assertThat(first.total()).isEqualByComparingTo(second.total());

        // Stock reserved only once (2 units, not 4 units)
        ParentOrder parentOrder = checkoutService.getOrder(first.orderId(), customer.getId());
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(parentOrder.getId());
        assertThat(reservations).hasSize(1);
    }

    @Test
    void testCheckout_InsufficientStock_RollsBackCleanly() {
        cartService.clearCart(customer.getId());
        // Set variant stock on-hand to 1
        inventoryService.setStock(warehouse.getPublicId(), variant.getPublicId().toString(), 1, 0, sellerUser.getId());

        cartService.addToCart(customer.getId(), variant.getPublicId().toString(), 1);

        CheckoutQuoteService.QuoteResult quote = quoteService.generateQuote(
                customer.getId(),
                address.getPublicId().toString(),
                "standard"
        );

        // Now drop available stock to 0 by someone else setting stock to 0
        inventoryService.setStock(warehouse.getPublicId(), variant.getPublicId().toString(), 0, 0, sellerUser.getId());

        String idempotencyKey = "chk_fail_" + UUID.randomUUID();

        assertThatThrownBy(() -> checkoutService.checkout(
                customer.getId(),
                quote.quoteId(),
                idempotencyKey,
                "MOCK_HOSTED",
                "http://localhost:3000"
        ))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));

        // Verify zero orders and zero reservations persisted
        assertThat(parentOrderRepository.findByIdempotencyKey(idempotencyKey)).isEmpty();
    }
}
