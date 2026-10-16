package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.authentication.domain.model.LoginOutcome;
import com.archimstechcorner.marketplace.features.authentication.domain.model.UserLoginEvent;
import com.archimstechcorner.marketplace.features.authentication.domain.model.UserSession;
import com.archimstechcorner.marketplace.features.authentication.domain.repository.UserLoginEventRepository;
import com.archimstechcorner.marketplace.features.authentication.domain.repository.UserSessionRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Isolated security outcome recorder.
 * All methods execute with Propagation.REQUIRES_NEW so that login failures, lockouts,
 * audit records, and session reuse revocations are durably committed to the database
 * regardless of whether the outer business transaction throws an exception or rolls back.
 */
@Service
public class AuthSecurityOutcomeService {

    private final UserRepository userRepository;
    private final UserLoginEventRepository loginEventRepository;
    private final UserSessionRepository sessionRepository;
    private final AuditService auditService;
    private final AuthProperties properties;

    public AuthSecurityOutcomeService(UserRepository userRepository,
                                      UserLoginEventRepository loginEventRepository,
                                      UserSessionRepository sessionRepository,
                                      AuditService auditService,
                                      AuthProperties properties) {
        this.userRepository = userRepository;
        this.loginEventRepository = loginEventRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(Long userId, String emailNormalized, DeviceInfo device, Instant now, String failureDetail) {
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                int attempts = user.getFailedLoginCount() + 1;
                if (attempts >= properties.getLockoutThreshold()) {
                    user.setLockedUntil(now.plusSeconds(properties.getLockoutMinutes() * 60L));
                    user.setFailedLoginCount(0);
                } else {
                    user.setFailedLoginCount(attempts);
                }
                userRepository.save(user);

                UserLoginEvent event = UserLoginEvent.create();
                event.setUserId(user.getId());
                event.setOutcome(LoginOutcome.FAILED);
                event.setReasonCode(failureDetail);
                event.setEmailAttempted(emailNormalized);
                event.setDeviceLabel(device.label());
                event.setIpHash(device.ipHash());
                event.setUserAgentHash(device.userAgentHash());
                event.setOccurredAt(now);
                loginEventRepository.save(event);
            });
        } else {
            UserLoginEvent event = UserLoginEvent.create();
            event.setOutcome(LoginOutcome.FAILED);
            event.setReasonCode(failureDetail);
            event.setEmailAttempted(emailNormalized);
            event.setDeviceLabel(device.label());
            event.setIpHash(device.ipHash());
            event.setUserAgentHash(device.userAgentHash());
            event.setOccurredAt(now);
            loginEventRepository.save(event);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginLockout(Long userId, String emailNormalized, DeviceInfo device, Instant now) {
        UserLoginEvent event = UserLoginEvent.create();
        event.setUserId(userId);
        event.setOutcome(LoginOutcome.LOCKED);
        event.setReasonCode("LOCKED");
        event.setEmailAttempted(emailNormalized);
        event.setDeviceLabel(device.label());
        event.setIpHash(device.ipHash());
        event.setUserAgentHash(device.userAgentHash());
        event.setOccurredAt(now);
        loginEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSessionReuseAndRevokeFamily(UUID familyId, Long userId, String publicSessionId, Instant now) {
        for (UserSession s : sessionRepository.findByRotationFamilyId(familyId)) {
            if (s.getRevokedAt() == null) {
                s.setRevokedAt(now);
                s.setRevokeReason("REUSE_DETECTED");
                sessionRepository.save(s);
            }
        }
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                user.bumpSecurityVersion();
                userRepository.save(user);
            });
        }
        auditService.record("SESSION_REUSE_DETECTED", "SESSION", publicSessionId, userId, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSessionExpired(Long sessionId, Instant now) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setRevokedAt(now);
            s.setRevokeReason("EXPIRED");
            sessionRepository.save(s);
        });
    }
}
