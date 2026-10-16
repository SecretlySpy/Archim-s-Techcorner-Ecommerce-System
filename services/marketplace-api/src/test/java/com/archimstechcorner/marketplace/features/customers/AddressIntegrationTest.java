package com.archimstechcorner.marketplace.features.customers;

import com.archimstechcorner.marketplace.features.customers.application.AddressService;
import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerAddressRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AddressIntegrationTest {

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
    private AddressService addressService;

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmailNormalizedAndDeletedAtIsNull("address_tester@example.com")
                .orElseGet(() -> {
                    User u = User.create();
                    u.setPublicId(java.util.UUID.randomUUID());
                    u.setEmailNormalized("address_tester@example.com");
                    u.setEmailDisplay("address_tester@example.com");
                    u.setPasswordHash("hashed_pw");
                    u.setStatus(com.archimstechcorner.marketplace.features.users.domain.model.UserStatus.ACTIVE);
                    return userRepository.save(u);
                });
    }

    @Test
    void canSaveMultipleNonDefaultAddressesForSameUserAndType() {
        // G04 Bug: In old schema, UNIQUE(user_id, address_type, is_default) blocked saving >1 non-default address.
        CustomerAddress addr1 = addressService.addAddress(
                testUser.getId(), "SHIPPING", "Home 1", "09170000001",
                "123 Maple St", null, "Makati", "Metro Manila", "1200", "PH", false
        );

        CustomerAddress addr2 = addressService.addAddress(
                testUser.getId(), "SHIPPING", "Home 2", "09170000002",
                "456 Oak Ave", "Unit 4B", "Pasig", "Metro Manila", "1600", "PH", false
        );

        assertThat(addr1.getId()).isNotNull();
        assertThat(addr2.getId()).isNotNull();
        assertThat(addr1.getVersion()).isNotNull();
        assertThat(addr2.getVersion()).isNotNull();

        List<CustomerAddress> addresses = addressService.listAddresses(testUser.getId());
        assertThat(addresses).extracting(CustomerAddress::getRecipientName)
                .contains("Home 1", "Home 2");
    }

    @Test
    void settingNewDefaultAddressUnsetsPreviousDefault() {
        CustomerAddress addr1 = addressService.addAddress(
                testUser.getId(), "SHIPPING", "Default First", "09170000003",
                "789 Pine St", null, "Taguig", "Metro Manila", "1630", "PH", true
        );
        assertThat(addr1.isDefault()).isTrue();

        CustomerAddress addr2 = addressService.addAddress(
                testUser.getId(), "SHIPPING", "Default Second", "09170000004",
                "101 Cedar Rd", null, "Quezon City", "Metro Manila", "1100", "PH", true
        );
        assertThat(addr2.isDefault()).isTrue();

        CustomerAddress reloaded1 = addressService.getAddress(testUser.getId(), addr1.getPublicId().toString());
        assertThat(reloaded1.isDefault()).isFalse();

        // Ensure exactly one default exists for testUser and SHIPPING
        List<CustomerAddress> defaults = addressRepository.findByUserId(testUser.getId()).stream()
                .filter(a -> a.getAddressType().equals("SHIPPING") && a.isDefault())
                .toList();
        assertThat(defaults).hasSize(1);
        assertThat(defaults.getFirst().getRecipientName()).isEqualTo("Default Second");
    }

    @Test
    void optimisticLockingPreventsStaleUpdates() {
        CustomerAddress addr = addressService.addAddress(
                testUser.getId(), "SHIPPING", "Original Name", "09170000005",
                "222 Elm St", null, "Mandaluyong", "Metro Manila", "1550", "PH", false
        );

        Long originalVersion = addr.getVersion();

        // Update with correct version succeeds
        CustomerAddress updated = addressService.updateAddress(
                testUser.getId(), addr.getPublicId().toString(), originalVersion,
                "Updated Name", null, null, null, null, null, null, null, null
        );
        assertThat(updated.getRecipientName()).isEqualTo("Updated Name");
        assertThat(updated.getVersion()).isGreaterThan(originalVersion);

        // Update with old/stale version throws IDEMPOTENCY_CONFLICT
        assertThatThrownBy(() -> addressService.updateAddress(
                testUser.getId(), addr.getPublicId().toString(), originalVersion,
                "Conflicting Name", null, null, null, null, null, null, null, null
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("Stale version");
    }
}
