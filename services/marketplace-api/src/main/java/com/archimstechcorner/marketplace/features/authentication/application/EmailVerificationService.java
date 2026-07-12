package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.authentication.application.port.EmailSender;
import com.archimstechcorner.marketplace.features.authentication.domain.model.EmailVerificationToken;
import com.archimstechcorner.marketplace.features.authentication.domain.model.TokenPurpose;
import com.archimstechcorner.marketplace.features.authentication.domain.repository.EmailVerificationTokenRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import com.archimstechcorner.marketplace.shared.support.EmailNormalizer;
import com.archimstechcorner.marketplace.shared.support.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Consumes email-verification tokens and re-issues them. Both flows are idempotent/anti-enumerating. */
@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final AuthProperties properties;
    private final Clock clock;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    EmailSender emailSender,
                                    AuditService auditService,
                                    AuthProperties properties,
                                    Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void verify(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokenRepository
                .findByTokenHashAndPurpose(SecureTokens.sha256Hex(rawToken), TokenPurpose.EMAIL_VERIFY)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Verification link is invalid or expired."));
        if (!token.isUsableAt(now)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Verification link is invalid or expired.");
        }

        token.setConsumedAt(now); // single-use
        tokenRepository.save(token);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Verification link is invalid or expired."));
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(now);
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                user.setStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);
            auditService.record("EMAIL_VERIFIED", "USER", user.getPublicId().toString(), user.getId(), null);
        }
    }

    /** Re-issues a verification email if the account exists and is not yet verified. Always safe to call. */
    @Transactional
    public void resend(String email) {
        String normalized = EmailNormalizer.normalize(email);
        userRepository.findByEmailNormalizedAndDeletedAtIsNull(normalized).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                String rawToken = SecureTokens.generate();
                EmailVerificationToken token = EmailVerificationToken.create();
                token.setUserId(user.getId());
                token.setTokenHash(SecureTokens.sha256Hex(rawToken));
                token.setPurpose(TokenPurpose.EMAIL_VERIFY);
                token.setExpiresAt(clock.instant().plusSeconds(properties.getVerificationTokenTtlMinutes() * 60L));
                tokenRepository.save(token);
                emailSender.sendVerification(email, rawToken);
            }
        });
    }
}
