package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.authentication.domain.model.LoginOutcome;
import com.archimstechcorner.marketplace.features.authentication.domain.model.UserLoginEvent;
import com.archimstechcorner.marketplace.features.authentication.domain.repository.UserLoginEventRepository;
import com.archimstechcorner.marketplace.features.users.application.AuthorityService;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.security.AccessTokenService;
import com.archimstechcorner.marketplace.shared.security.AuthProperties;
import com.archimstechcorner.marketplace.shared.support.EmailNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Verifies credentials and issues a session. Applies durable (MySQL-backed) lockout after repeated
 * failures and records every attempt to login history. All failure paths return the same
 * {@code INVALID_CREDENTIALS} response so an attacker cannot enumerate accounts.
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final UserLoginEventRepository loginEventRepository;
    private final AuditService auditService;
    private final AuthProperties properties;
    private final Clock clock;
    private final MfaService mfaService;
    private final AccessTokenService accessTokenService;
    private final AuthorityService authorityService;

    /** A real, properly-formatted hash used to equalize timing when the email does not resolve to a
     *  user, so an attacker cannot distinguish "no such account" from "wrong password" by response time. */
    private final String timingEqualizerHash;

    public LoginService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        SessionService sessionService,
                        UserLoginEventRepository loginEventRepository,
                        AuditService auditService,
                        AuthProperties properties,
                        Clock clock,
                        MfaService mfaService,
                        AccessTokenService accessTokenService,
                        AuthorityService authorityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.loginEventRepository = loginEventRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
        this.mfaService = mfaService;
        this.accessTokenService = accessTokenService;
        this.authorityService = authorityService;
        // Encoded by the same delegating encoder, so it carries the {id} prefix that matches() requires.
        this.timingEqualizerHash = passwordEncoder.encode("timing-equalizer-value");
    }

    @Transactional
    public LoginResult login(String email, String rawPassword, DeviceInfo device) {
        Instant now = clock.instant();
        String normalized = EmailNormalizer.normalize(email);
        User user = userRepository.findByEmailNormalizedAndDeletedAtIsNull(normalized).orElse(null);

        if (user == null) {
            // Run a real hash comparison against a properly-formatted dummy hash to keep timing uniform,
            // then fail identically to a wrong password. Using a raw (un-prefixed) hash here would make
            // the delegating encoder throw, turning a clean 401 into a 500 and leaking that the email
            // does not exist — so we compare against the constructor-built timingEqualizerHash.
            passwordEncoder.matches(rawPassword, timingEqualizerHash);
            recordEvent(null, LoginOutcome.FAILED, "NO_USER", normalized, device, now);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }
        if (user.isLockedAt(now)) {
            recordEvent(user, LoginOutcome.LOCKED, "LOCKED", normalized, device, now);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked. Try again later.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED
                || user.getStatus() == UserStatus.DISABLED
                || user.getStatus() == UserStatus.DELETED) {
            recordEvent(user, LoginOutcome.FAILED, "INACTIVE", normalized, device, now);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailure(user, now);
            recordEvent(user, LoginOutcome.FAILED, "BAD_PASSWORD", normalized, device, now);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }

        // Success: clear the failure counters. (Unverified users may sign in but cannot check out.)
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        boolean hasMfa = mfaService.isMfaEnabled(user.getId());
        List<String> authorities = authorityService.resolve(user.getId()).grantedAuthorityStrings();
        boolean requiresMfa = hasMfa || authorities.stream().anyMatch(role -> 
                role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPPORT") || 
                role.equals("ROLE_FINANCE") || role.equals("ROLE_CONTENT"));

        if (requiresMfa) {
            String challengeToken = accessTokenService.issueMfaChallenge(user.getId());
            return new LoginResult(null, user.getPublicId(), user.getEmailDisplay(), user.isEmailVerified(), challengeToken);
        }

        IssuedTokens tokens = sessionService.issueNewSession(user, device);
        recordEvent(user, LoginOutcome.SUCCESS, null, normalized, device, now);
        auditService.record("LOGIN_SUCCEEDED", "USER", user.getPublicId().toString(), user.getId(), null);
        return new LoginResult(tokens, user.getPublicId(), user.getEmailDisplay(), user.isEmailVerified(), null);
    }

    @Transactional
    public LoginResult loginMfa(String mfaChallengeToken, String code, DeviceInfo device) {
        Long userId = accessTokenService.parseMfaChallenge(mfaChallengeToken)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid or expired MFA challenge."));

        User user = userRepository.findById(userId).orElseThrow();
        Instant now = clock.instant();

        if (user.isLockedAt(now)) {
            recordEvent(user, LoginOutcome.LOCKED, "LOCKED", user.getEmailNormalized(), device, now);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked. Try again later.");
        }

        if (!mfaService.verifyTotp(userId, code)) {
            registerFailure(user, now);
            recordEvent(user, LoginOutcome.FAILED, "BAD_MFA", user.getEmailNormalized(), device, now);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid MFA code.");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        IssuedTokens tokens = sessionService.issueNewSession(user, device);
        recordEvent(user, LoginOutcome.SUCCESS, null, user.getEmailNormalized(), device, now);
        auditService.record("LOGIN_SUCCEEDED", "USER", user.getPublicId().toString(), user.getId(), null);
        return new LoginResult(tokens, user.getPublicId(), user.getEmailDisplay(), user.isEmailVerified(), null);
    }

    private void registerFailure(User user, Instant now) {
        int attempts = user.getFailedLoginCount() + 1;
        if (attempts >= properties.getLockoutThreshold()) {
            // Durable lock lives in MySQL; a Redis flush can never unlock the account early.
            user.setLockedUntil(now.plusSeconds(properties.getLockoutMinutes() * 60L));
            user.setFailedLoginCount(0);
        } else {
            user.setFailedLoginCount(attempts);
        }
        userRepository.save(user);
    }

    private void recordEvent(User user, LoginOutcome outcome, String reason,
                             String emailAttempted, DeviceInfo device, Instant now) {
        UserLoginEvent event = UserLoginEvent.create();
        event.setUserId(user == null ? null : user.getId());
        event.setOutcome(outcome);
        event.setReasonCode(reason);
        event.setEmailAttempted(emailAttempted);
        event.setIpHash(device.ipHash());
        event.setUserAgentHash(device.userAgentHash());
        event.setDeviceLabel(device.label());
        event.setOccurredAt(now);
        loginEventRepository.save(event);
    }
}
