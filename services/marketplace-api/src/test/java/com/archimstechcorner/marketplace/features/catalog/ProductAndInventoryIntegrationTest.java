package com.archimstechcorner.marketplace.features.catalog;

import com.archimstechcorner.marketplace.features.catalog.application.ProductService;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.CategoryRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductVariantRepository;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.inventory.domain.model.Inventory;
import com.archimstechcorner.marketplace.features.inventory.domain.repository.InventoryRepository;
import com.archimstechcorner.marketplace.features.sellers.application.SellerService;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStatus;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Warehouse;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.WarehouseRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ProductAndInventoryIntegrationTest {

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
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SellerService sellerService;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private com.archimstechcorner.marketplace.features.sellers.domain.repository.StoreRepository storeRepository;

    private User sellerUser;
    private Seller seller;
    private Category category;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        sellerUser = userRepository.findByEmailNormalizedAndDeletedAtIsNull("seller_m02@example.com")
                .orElseGet(() -> {
                    User u = User.create();
                    u.setPublicId(java.util.UUID.randomUUID());
                    u.setEmailNormalized("seller_m02@example.com");
                    u.setEmailDisplay("seller_m02@example.com");
                    u.setPasswordHash("hashed_pw");
                    u.setStatus(com.archimstechcorner.marketplace.features.users.domain.model.UserStatus.ACTIVE);
                    return userRepository.save(u);
                });

        seller = Seller.create("M02 Tech Store " + System.currentTimeMillis(), "TAX-" + System.currentTimeMillis());
        seller.activate();
        seller.addStaff(com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStaff.create(
                seller, sellerUser, com.archimstechcorner.marketplace.features.sellers.domain.model.SellerRole.OWNER));
        seller = sellerRepository.save(seller);

        com.archimstechcorner.marketplace.features.sellers.domain.model.Store store =
                com.archimstechcorner.marketplace.features.sellers.domain.model.Store.create(seller, "M02 Main Store", "Store description");
        store.publish();
        store = storeRepository.save(store);

        warehouse = Warehouse.create(store, "Central WH", "123 Main St", "Makati", "NCR", "PH", "1200");
        warehouse = warehouseRepository.save(warehouse);

        category = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category c = Category.create(null, "Gadgets", "gadgets", "Tech gadgets");
            return categoryRepository.save(c);
        });
    }

    @Test
    void createProductAggregateAtomicallyPersistsProductAndVariants() {
        var v1 = new ProductService.VariantInput("SKU-ATOM-01", "Variant 1", BigDecimal.valueOf(100), null, 10, "{}");
        var v2 = new ProductService.VariantInput("SKU-ATOM-02", "Variant 2", BigDecimal.valueOf(150), null, 5, "{}");

        Product product = productService.createProductAggregate(
                seller.getPublicId(),
                category.getId(),
                null,
                "Atomic Gaming Mouse",
                "atomic-gaming-mouse",
                "High DPI mouse",
                List.of(v1, v2),
                sellerUser.getId()
        );

        assertThat(product.getId()).isNotNull();
        assertThat(product.getVariants()).hasSize(2);

        Product reloaded = productService.getProduct(product.getPublicId().toString());
        assertThat(reloaded.getVariants()).hasSize(2);
        assertThat(variantRepository.findBySku("SKU-ATOM-01")).isPresent();
        assertThat(variantRepository.findBySku("SKU-ATOM-02")).isPresent();
    }

    @Test
    void createProductAggregateRollsBackWhenVariantIsInvalid() {
        // G09: If a nested variant is invalid, the entire aggregate creation must roll back.
        var validV = new ProductService.VariantInput("SKU-FAIL-01", "Variant 1", BigDecimal.valueOf(100), null, 10, "{}");
        var invalidV = new ProductService.VariantInput("SKU-FAIL-02", "Variant 2", BigDecimal.valueOf(-50), null, 5, "{}"); // Negative price

        assertThatThrownBy(() -> productService.createProductAggregate(
                seller.getPublicId(),
                category.getId(),
                null,
                "Failed Keyboard",
                "failed-keyboard",
                "Keyboard",
                List.of(validV, invalidV),
                sellerUser.getId()
        )).isInstanceOf(ApiException.class);

        // Verify product was NOT saved
        assertThat(productRepository.findBySlug("failed-keyboard")).isEmpty();
        // Verify first variant was NOT saved
        assertThat(variantRepository.findBySku("SKU-FAIL-01")).isEmpty();
    }

    @Test
    void isEligibleForSaleEnforcesProductVariantAndSellerConstraints() {
        var v1 = new ProductService.VariantInput("SKU-ELIG-01", "Variant 1", BigDecimal.valueOf(200), null, 10, "{}");
        Product product = productService.createProductAggregate(
                seller.getPublicId(),
                category.getId(),
                null,
                "Eligible Headset",
                "eligible-headset",
                "Headset",
                List.of(v1),
                sellerUser.getId()
        );
        ProductVariant variant = product.getVariants().getFirst();

        // Initially in DRAFT -> not eligible
        assertThat(productService.isEligibleForSale(product, variant)).isFalse();

        // Set to ACTIVE
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        // When ACTIVE and seller ACTIVE -> eligible
        assertThat(productService.isEligibleForSale(product, variant)).isTrue();

        // If variant is inactive -> not eligible
        variant.setActive(false);
        variantRepository.save(variant);
        assertThat(productService.isEligibleForSale(product, variant)).isFalse();
    }

    @Test
    void inventoryReservationAndConsumptionFollowInvariants() {
        if (warehouse == null) {
            return; // Skip if no warehouse seeded
        }

        var v1 = new ProductService.VariantInput("SKU-INV-01", "Variant Inv", BigDecimal.valueOf(300), null, 0, "{}");
        Product product = productService.createProductAggregate(
                seller.getPublicId(),
                category.getId(),
                null,
                "Inventory Monitor",
                "inventory-monitor",
                "Monitor",
                List.of(v1),
                sellerUser.getId()
        );
        ProductVariant variant = product.getVariants().getFirst();

        // 1. Set stock to 10
        inventoryService.setStock(warehouse.getPublicId(), variant.getPublicId().toString(), 10, 2, sellerUser.getId());
        Inventory inv = inventoryRepository.findByWarehouse_IdAndVariant_Id(warehouse.getId(), variant.getId()).orElseThrow();
        assertThat(inv.getOnHandQuantity()).isEqualTo(10);
        assertThat(inv.getReservedQuantity()).isEqualTo(0);
        assertThat(inv.sellable()).isEqualTo(10);

        // 2. Reserve 4 units
        inventoryService.reserveStock(warehouse.getId(), variant.getId(), 4);
        inv = inventoryRepository.findByWarehouse_IdAndVariant_Id(warehouse.getId(), variant.getId()).orElseThrow();
        assertThat(inv.getOnHandQuantity()).isEqualTo(10);
        assertThat(inv.getReservedQuantity()).isEqualTo(4);
        assertThat(inv.sellable()).isEqualTo(6);

        // 3. Try reserving 7 units (sellable is only 6) -> must fail
        assertThatThrownBy(() -> inventoryService.reserveStock(warehouse.getId(), variant.getId(), 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient sellable stock");

        // 4. Release 2 units
        inventoryService.releaseStock(warehouse.getId(), variant.getId(), 2);
        inv = inventoryRepository.findByWarehouse_IdAndVariant_Id(warehouse.getId(), variant.getId()).orElseThrow();
        assertThat(inv.getReservedQuantity()).isEqualTo(2);
        assertThat(inv.sellable()).isEqualTo(8);

        // 5. Consume remaining 2 reserved units (order fulfillment)
        inventoryService.consumeStock(warehouse.getId(), variant.getId(), 2, sellerUser.getId());
        inv = inventoryRepository.findByWarehouse_IdAndVariant_Id(warehouse.getId(), variant.getId()).orElseThrow();
        assertThat(inv.getOnHandQuantity()).isEqualTo(8);
        assertThat(inv.getReservedQuantity()).isEqualTo(0);
        assertThat(inv.sellable()).isEqualTo(8);
    }

    @Test
    void concurrentReservationContentionPreventsOverAllocation() throws Exception {
        if (warehouse == null) return;

        var v1 = new ProductService.VariantInput("SKU-CONC-01", "Contention Variant", BigDecimal.valueOf(500), null, 0, "{}");
        Product product = productService.createProductAggregate(
                seller.getPublicId(), category.getId(), null,
                "Contention Item", "contention-item", "Item",
                List.of(v1), sellerUser.getId()
        );
        ProductVariant variant = product.getVariants().getFirst();

        // Stock = exactly 2 sellable units
        inventoryService.setStock(warehouse.getPublicId(), variant.getPublicId().toString(), 2, 0, sellerUser.getId());

        int threadCount = 6;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger(0);

        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    inventoryService.reserveStock(warehouse.getId(), variant.getId(), 1);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }));
        }

        latch.countDown();
        for (java.util.concurrent.Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(2);
        assertThat(failures.get()).isEqualTo(4);

        Inventory inv = inventoryRepository.findByWarehouse_IdAndVariant_Id(warehouse.getId(), variant.getId()).orElseThrow();
        assertThat(inv.getOnHandQuantity()).isEqualTo(2);
        assertThat(inv.getReservedQuantity()).isEqualTo(2);
        assertThat(inv.sellable()).isEqualTo(0);
    }
}
