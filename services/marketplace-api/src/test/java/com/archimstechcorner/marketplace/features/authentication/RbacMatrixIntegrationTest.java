package com.archimstechcorner.marketplace.features.authentication;

import com.archimstechcorner.marketplace.features.users.api.AssignRoleRequest;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validates the RBAC Matrix rules defined in documentation/security/rbac-matrix.md
 * specifically checking that the @PreAuthorize gates correctly enforce deny-by-default
 * and only allow specific permission codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RbacMatrixIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("auth.jwt-secret", () -> "integration_test_secret_key_long_enough_1234567890");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private AuthenticatedUser mockUser(String role, String permission) {
        return new AuthenticatedUser(
                1L, UUID.randomUUID(), "test@example.com", 1,
                List.of(role, permission)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "users.role.assign, 204",     // Granted -> Should allow (or 404/etc if entity missing, but not 403)
            "some.other.permission, 403"  // Denied -> Should block
    })
    void testAssignRoleAuthorization(String permission, int expectedStatus) throws Exception {
        AuthenticatedUser principal = mockUser("ROLE_ADMIN", permission);
        AssignRoleRequest request = new AssignRoleRequest("CUSTOMER", null);

        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(permission))))))
                .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @CsvSource({
            "users.role.assign, 204",
            "some.other.permission, 403"
    })
    void testRevokeRoleAuthorization(String permission, int expectedStatus) throws Exception {
        AuthenticatedUser principal = mockUser("ROLE_ADMIN", permission);

        mvc.perform(delete("/api/v1/users/" + UUID.randomUUID() + "/roles/CUSTOMER")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(permission))))))
                .andExpect(status().is(expectedStatus));
    }
}
