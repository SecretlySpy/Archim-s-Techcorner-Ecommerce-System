package com.archimstechcorner.marketplace.features.authentication;

import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth tests against the real migrated schema in a throwaway MySQL 8 container.
 * Proves the Flyway chain applies, the RBAC seed loads, and the register/login/authorize flow works.
 * Requires Docker (as CI provides). Unit tests in {@code shared/security} run without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // Point both the app datasource and Flyway at the container.
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
    private UserRepository userRepository;

    @Test
    void registeringCustomerPersistsUnverifiedUser() throws Exception {
        mvc.perform(post("/api/v1/auth/register/customer").contentType(APPLICATION_JSON).content("""
                        {"email":"alice@example.com","password":"SuperSecret123","displayName":"Alice"}"""))
                .andExpect(status().isAccepted());

        User user = userRepository.findByEmailNormalizedAndDeletedAtIsNull("alice@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        register("bob@example.com", "GoodPassword123", "Bob");

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email":"bob@example.com","password":"WrongPassword999"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginSucceedsAndAuthorizesCurrentUser() throws Exception {
        register("carol@example.com", "GoodPassword123", "Carol");

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email":"carol@example.com","password":"GoodPassword123"}"""))
                .andExpect(status().isOk())
                .andReturn();

        // Reuse the auth cookies set by login to call a protected endpoint.
        Cookie[] cookies = login.getResponse().getCookies();
        mvc.perform(get("/api/v1/users/me").cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carol@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    private void register(String email, String password, String displayName) throws Exception {
        mvc.perform(post("/api/v1/auth/register/customer").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\"}"
                                .formatted(email, password, displayName)))
                .andExpect(status().isAccepted());
    }
}
