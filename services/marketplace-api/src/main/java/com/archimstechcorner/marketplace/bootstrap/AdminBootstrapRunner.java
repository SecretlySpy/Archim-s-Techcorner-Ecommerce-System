package com.archimstechcorner.marketplace.bootstrap;

import com.archimstechcorner.marketplace.features.users.application.RoleAdminService;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.support.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Ensures a bootstrap administrator exists so the platform is operable without a manual
 * {@code user_roles} SQL insert. Guarded to be safe by default: it only acts when
 * {@code app.bootstrap.admin.enabled=true} AND a non-blank password is supplied (via secret/env).
 * In production, leave the password unset so this is a no-op; provision admins through the proper flow.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RoleAdminService roleAdminService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final boolean enabled;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(UserRepository userRepository,
                                RoleAdminService roleAdminService,
                                PasswordEncoder passwordEncoder,
                                Clock clock,
                                @Value("${app.bootstrap.admin.enabled:false}") boolean enabled,
                                @Value("${app.bootstrap.admin.email:admin@archims.local}") String email,
                                @Value("${app.bootstrap.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.roleAdminService = roleAdminService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || password == null || password.isBlank()) {
            return; // disabled or no password provided → no-op (safe default)
        }

        String normalized = EmailNormalizer.normalize(email);
        User admin = userRepository.findByEmailNormalizedAndDeletedAtIsNull(normalized).orElse(null);

        if (admin == null) {
            admin = User.create();
            admin.setPublicId(UUID.randomUUID());
            admin.setEmailNormalized(normalized);
            admin.setEmailDisplay(email.trim());
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setPasswordAlgo("bcrypt");
            admin.setStatus(UserStatus.ACTIVE);              // ready to use immediately
            admin.setEmailVerifiedAt(clock.instant());       // bootstrap admin is pre-verified
            admin = userRepository.save(admin);
            log.info("Bootstrap admin created: {}", normalized);
        }

        // Idempotent: grants ADMIN only if not already active (safe on every restart).
        roleAdminService.assignRole(admin.getPublicId(), "ADMIN", null, admin.getId());
        log.info("Bootstrap admin ensured with ADMIN role: {}", normalized);
    }
}
