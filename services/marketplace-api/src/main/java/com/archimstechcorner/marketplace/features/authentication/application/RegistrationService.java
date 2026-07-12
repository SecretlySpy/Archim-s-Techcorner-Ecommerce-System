package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.authentication.application.port.EmailSender;
import com.archimstechcorner.marketplace.features.authentication.domain.model.EmailVerificationToken;
import com.archimstechcorner.marketplace.features.authentication.domain.model.TokenPurpose;
import com.archimstechcorner.marketplace.features.authentication.domain.repository.EmailVerificationTokenRepository;
import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerProfile;
import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerProfileRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.Role;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserRole;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.RoleRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRoleRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import com.archimstechcorner.marketplace.shared.support.EmailNormalizer;
import com.archimstechcorner.marketplace.shared.support.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Customer self-registration. Creates the user, customer profile, and {@code CUSTOMER} role atomically,
 * then issues an email-verification token. Enumeration-resistant: an already-registered email yields the
 * same outcome as a fresh one (the controller responds {@code 202} either way).
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final AuthProperties properties;
    private final Clock clock;

    public RegistrationService(UserRepository userRepository,
                               CustomerProfileRepository customerProfileRepository,
                               RoleRepository roleRepository,
                               UserRoleRepository userRoleRepository,
                               EmailVerificationTokenRepository tokenRepository,
                               PasswordEncoder passwordEncoder,
                               EmailSender emailSender,
                               AuditService auditService,
                               AuthProperties properties,
                               Clock clock) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void registerCustomer(String email, String rawPassword, String displayName) {
        String normalized = EmailNormalizer.normalize(email);

        // Anti-enumeration: never reveal whether the email already exists.
        if (userRepository.existsByEmailNormalized(normalized)) {
            return;
        }

        User user = User.create();
        user.setPublicId(UUID.randomUUID());
        user.setEmailNormalized(normalized);
        user.setEmailDisplay(email.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword)); // one-way adaptive hash
        user.setPasswordAlgo("bcrypt");
        user.setStatus(UserStatus.PENDING_VERIFICATION);           // cannot check out until verified
        userRepository.save(user);

        CustomerProfile profile = CustomerProfile.create();
        profile.setPublicId(UUID.randomUUID());
        profile.setUserId(user.getId());
        profile.setDisplayName(displayName.trim());
        customerProfileRepository.save(profile);

        assignCustomerRole(user);
        issueVerificationToken(user, email);

        auditService.record("USER_REGISTERED", "USER", user.getPublicId().toString(), null, "role=CUSTOMER");
    }

    private void assignCustomerRole(User user) {
        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "CUSTOMER role is not seeded."));
        UserRole assignment = UserRole.create();
        assignment.setUserId(user.getId());
        assignment.setRoleId(customerRole.getId());
        assignment.setActiveMarker(UserRole.ACTIVE);
        assignment.setGrantedAt(clock.instant()); // self-granted at registration; grantedBy is null
        userRoleRepository.save(assignment);
    }

    private void issueVerificationToken(User user, String email) {
        String rawToken = SecureTokens.generate();
        EmailVerificationToken token = EmailVerificationToken.create();
        token.setUserId(user.getId());
        token.setTokenHash(SecureTokens.sha256Hex(rawToken)); // store hash only
        token.setPurpose(TokenPurpose.EMAIL_VERIFY);
        token.setExpiresAt(clock.instant().plusSeconds(properties.getVerificationTokenTtlMinutes() * 60L));
        tokenRepository.save(token);
        emailSender.sendVerification(email, rawToken); // raw token leaves only via the email channel
    }
}
