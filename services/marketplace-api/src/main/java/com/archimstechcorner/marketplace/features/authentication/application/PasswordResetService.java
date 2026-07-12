package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.authentication.application.port.EmailSender;
import com.archimstechcorner.marketplace.features.authentication.domain.model.EmailVerificationToken;
import com.archimstechcorner.marketplace.features.authentication.domain.model.TokenPurpose;
import com.archimstechcorner.marketplace.features.authentication.domain.repository.EmailVerificationTokenRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import com.archimstechcorner.marketplace.shared.support.EmailNormalizer;
import com.archimstechcorner.marketplace.shared.support.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Password reset. {@link #requestReset} is anti-enumerating (always succeeds silently). A completed
 * {@link #reset} bumps the security version and revokes all sessions, so any stolen token/cookie dies.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository,
                                EmailVerificationTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                SessionService sessionService,
                                EmailSender emailSender,
                                AuditService auditService,
                                AuthProperties properties,
                                Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        String normalized = EmailNormalizer.normalize(email);
        userRepository.findByEmailNormalizedAndDeletedAtIsNull(normalized).ifPresent(user -> {
            String rawToken = SecureTokens.generate();
            EmailVerificationToken token = EmailVerificationToken.create();
            token.setUserId(user.getId());
            token.setTokenHash(SecureTokens.sha256Hex(rawToken));
            token.setPurpose(TokenPurpose.PASSWORD_RESET);
            token.setExpiresAt(clock.instant().plusSeconds(properties.getResetTokenTtlMinutes() * 60L));
            tokenRepository.save(token);
            emailSender.sendPasswordReset(email, rawToken);
        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokenRepository
                .findByTokenHashAndPurpose(SecureTokens.sha256Hex(rawToken), TokenPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Reset link is invalid or expired."));
        if (!token.isUsableAt(now)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Reset link is invalid or expired.");
        }

        token.setConsumedAt(now); // single-use
        tokenRepository.save(token);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Reset link is invalid or expired."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setSecurityVersion(user.getSecurityVersion() + 1); // invalidate outstanding access tokens
        userRepository.save(user);

        sessionService.revokeAllForUser(user.getId(), "PASSWORD_RESET"); // kill every refresh session
        auditService.record("PASSWORD_RESET", "USER", user.getPublicId().toString(), user.getId(), null);
    }
}
