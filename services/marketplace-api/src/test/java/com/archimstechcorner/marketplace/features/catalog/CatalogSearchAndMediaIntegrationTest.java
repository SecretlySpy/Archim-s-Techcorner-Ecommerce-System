package com.archimstechcorner.marketplace.features.catalog;

import com.archimstechcorner.marketplace.features.catalog.application.FeedPage;
import com.archimstechcorner.marketplace.features.catalog.application.ProductService;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.CategoryRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerRole;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStaff;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
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

@SpringBootTest
@Testcontainers
class CatalogSearchAndMediaIntegrationTest {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private UserRepository userRepository;

    private User sellerUser;
    private Seller seller;
    private Category catPhones;
    private Category catLaptops;

    @BeforeEach
    void setUp() {
        sellerUser = userRepository.findByEmailNormalizedAndDeletedAtIsNull("catalog_m03@example.com")
                .orElseGet(() -> {
                    User u = User.create();
                    u.setPublicId(UUID.randomUUID());
                    u.setEmailNormalized("catalog_m03@example.com");
                    u.setEmailDisplay("catalog_m03@example.com");
                    u.setPasswordHash("hashed_pw");
                    u.setStatus(UserStatus.ACTIVE);
                    return userRepository.save(u);
                });

        seller = Seller.create("Search Store " + System.currentTimeMillis(), "TAX-" + System.currentTimeMillis());
        seller.activate();
        seller.addStaff(SellerStaff.create(seller, sellerUser, SellerRole.OWNER));
        seller = sellerRepository.save(seller);

        catPhones = categoryRepository.findAll().stream()
                .filter(c -> c.getSlug().equals("phones"))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(Category.create(null, "Phones", "phones", "Mobile phones")));

        catLaptops = categoryRepository.findAll().stream()
                .filter(c -> c.getSlug().equals("laptops"))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(Category.create(null, "Laptops", "laptops", "Laptops & Notebooks")));
    }

    @Test
    void composableFeedFiltersByCategoryAndSearchQueryConcurrently() {
        // G16: Test that category AND text query compose rather than being mutually exclusive
        var v1 = new ProductService.VariantInput("SKU-PH-PRO", "Pro 256GB", BigDecimal.valueOf(999), null, 10, "{}");
        Product phonePro = productService.createProductAggregate(
                seller.getPublicId(), catPhones.getId(), null,
                "Apex Phone Pro", "apex-phone-pro-" + System.currentTimeMillis(), "Pro smartphone",
                List.of(v1), sellerUser.getId()
        );
        phonePro.setStatus(ProductStatus.ACTIVE);
        productRepository.save(phonePro);

        var v2 = new ProductService.VariantInput("SKU-PH-LITE", "Lite 64GB", BigDecimal.valueOf(399), null, 10, "{}");
        Product phoneLite = productService.createProductAggregate(
                seller.getPublicId(), catPhones.getId(), null,
                "Budget Phone Lite", "budget-phone-lite-" + System.currentTimeMillis(), "Budget phone",
                List.of(v2), sellerUser.getId()
        );
        phoneLite.setStatus(ProductStatus.ACTIVE);
        productRepository.save(phoneLite);

        var v3 = new ProductService.VariantInput("SKU-LAP-PRO", "Ultra Pro", BigDecimal.valueOf(1999), null, 5, "{}");
        Product laptopPro = productService.createProductAggregate(
                seller.getPublicId(), catLaptops.getId(), null,
                "Apex Laptop Pro", "apex-laptop-pro-" + System.currentTimeMillis(), "Workstation laptop",
                List.of(v3), sellerUser.getId()
        );
        laptopPro.setStatus(ProductStatus.ACTIVE);
        productRepository.save(laptopPro);

        // 1. Query for "Pro" in "Phones" category -> must return ONLY "Apex Phone Pro"
        FeedPage page = productService.getFeed(0, 10, catPhones.getId(), null, null, null, null, "Pro", "newest");
        assertThat(page.items()).extracting(com.archimstechcorner.marketplace.features.catalog.application.ProductFeedItem::name)
                .contains("Apex Phone Pro")
                .doesNotContain("Apex Laptop Pro")  // Wrong category
                .doesNotContain("Budget Phone Lite"); // Does not match "Pro"
    }

    @Test
    void addSpecificationAndImagesToProduct() {
        var v = new ProductService.VariantInput("SKU-SPEC-01", "Standard", BigDecimal.valueOf(500), null, 10, "{}");
        Product product = productService.createProductAggregate(
                seller.getPublicId(), catPhones.getId(), null,
                "Spec Test Phone", "spec-test-phone-" + System.currentTimeMillis(), "Spec phone",
                List.of(v), sellerUser.getId()
        );

        // Add specifications
        productService.addSpecification(product.getPublicId().toString(), "RAM", "16GB", 1, sellerUser.getId());
        productService.addSpecification(product.getPublicId().toString(), "Storage", "512GB", 2, sellerUser.getId());

        // Add images with primary toggle
        productService.addImage(product.getPublicId().toString(), "https://example.com/img1.jpg", true, 1, sellerUser.getId());
        productService.addImage(product.getPublicId().toString(), "https://example.com/img2.jpg", false, 2, sellerUser.getId());

        Product reloaded = productService.getProduct(product.getPublicId().toString());
        assertThat(reloaded.getSpecifications()).hasSize(2);
        assertThat(reloaded.getImages()).hasSize(2);
        assertThat(reloaded.getImages().stream().filter(com.archimstechcorner.marketplace.features.catalog.domain.model.ProductImage::isPrimary))
                .hasSize(1);
    }
}
