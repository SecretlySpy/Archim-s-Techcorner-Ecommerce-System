package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.authentication.domain.model.UserSession;
import com.archimstechcorner.marketplace.features.authentication.domain.repository.UserSessionRepository;
import com.archimstechcorner.marketplace.features.users.application.AuthorityService;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.security.AccessTokenService;
import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import com.archimstechcorner.marketplace.shared.support.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Owns the refresh-session lifecycle: issue, rotate (with reuse detection), and revoke. Access tokens
 * are minted alongside each session from the user's current authorities.
 */
@Service
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AuthorityService authorityService;
    private final AccessTokenService accessTokenService;
    private final AuditService auditService;
    private final AuthSecurityOutcomeService securityOutcomeService;
    private final AuthProperties properties;
    private final Clock clock;

    public SessionService(UserSessionRepository sessionRepository,
                          UserRepository userRepository,
                          AuthorityService authorityService,
                          AccessTokenService accessTokenService,
                          AuditService auditService,
                          AuthSecurityOutcomeService securityOutcomeService,
                          AuthProperties properties,
                          Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.authorityService = authorityService;
        this.accessTokenService = accessTokenService;
        this.auditService = auditService;
        this.securityOutcomeService = securityOutcomeService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a brand-new session family (used at login) and returns the token pair. */
    @Transactional
    public IssuedTokens issueNewSession(User user, DeviceInfo device) {
        return createSession(user, UUID.randomUUID(), device);
    }

    /**
     * Rotates a presented refresh token. Detects reuse of an already-rotated/revoked token and, when it
     * occurs, revokes the whole family and bumps the user's security version (invalidating access tokens).
     */
    @Transactional
    public IssuedTokens refresh(String rawRefreshToken, DeviceInfo device) {
        Instant now = clock.instant();
        String hash = SecureTokens.sha256Hex(rawRefreshToken);
        UserSession current = sessionRepository.findByTokenHashWithLock(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_REVOKED, "Session is no longer valid."));

        if (current.getRevokedAt() != null) {
            // The token was already retired — presenting it again means it leaked. Kill the family.
            securityOutcomeService.recordSessionReuseAndRevokeFamily(
                    current.getRotationFamilyId(), current.getUserId(), current.getPublicId().toString(), now);
            throw new ApiException(ErrorCode.SESSION_REVOKED, "Session is no longer valid.");
        }
        if (current.getExpiresAt().isBefore(now)) {
            securityOutcomeService.recordSessionExpired(current.getId(), now);
            throw new ApiException(ErrorCode.SESSION_REVOKED, "Session is no longer valid.");
        }

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_REVOKED, "Session is no longer valid."));

        // Mint the successor in the same family, then retire the presented token.
        IssuedTokens tokens = createSession(user, current.getRotationFamilyId(), device);
        current.setRevokedAt(now);
        current.setRevokeReason("ROTATED");
        current.setLastUsedAt(now);
        sessionRepository.save(current);
        return tokens;
    }

    /** Revokes a single session (logout). Safe to call with an unknown token. */
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = SecureTokens.sha256Hex(rawRefreshToken);
        sessionRepository.findByTokenHash(hash).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(clock.instant());
                session.setRevokeReason("LOGOUT");
                sessionRepository.save(session);
            }
        });
    }

    /** Revokes every active session for a user (used after a password reset). */
    @Transactional
    public void revokeAllForUser(Long userId, String reason) {
        Instant now = clock.instant();
        for (UserSession session : sessionRepository.findByUserIdAndRevokedAtIsNull(userId)) {
            session.setRevokedAt(now);
            session.setRevokeReason(reason);
            sessionRepository.save(session);
        }
    }

    private IssuedTokens createSession(User user, UUID rotationFamilyId, DeviceInfo device) {
        Instant now = clock.instant();
        String rawRefresh = SecureTokens.generate();

        UserSession session = UserSession.create();
        session.setPublicId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setTokenHash(SecureTokens.sha256Hex(rawRefresh));
        session.setRotationFamilyId(rotationFamilyId);
        session.setDeviceLabel(device.label());
        session.setIpHash(device.ipHash());
        session.setUserAgentHash(device.userAgentHash());
        session.setIssuedAt(now);
        session.setExpiresAt(now.plusSeconds(properties.getRefreshTokenTtlSeconds()));
        sessionRepository.save(session);

        var authorities = authorityService.resolve(user.getId()).grantedAuthorityStrings();
        String accessToken = accessTokenService.issue(
                user.getId(), user.getPublicId(), user.getEmailDisplay(), user.getSecurityVersion(), authorities);
        return new IssuedTokens(accessToken, rawRefresh);
    }

    private void revokeFamily(UUID rotationFamilyId, String reason, Instant now) {
        for (UserSession session : sessionRepository.findByRotationFamilyId(rotationFamilyId)) {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
                session.setRevokeReason(reason);
                sessionRepository.save(session);
            }
        }
    }

    private void bumpSecurityVersion(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setSecurityVersion(user.getSecurityVersion() + 1);
            userRepository.save(user);
        });
    }
}
