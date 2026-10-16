package com.archimstechcorner.marketplace.features.authentication.api;

import com.archimstechcorner.marketplace.features.authentication.application.DeviceInfo;
import com.archimstechcorner.marketplace.features.authentication.application.EmailVerificationService;
import com.archimstechcorner.marketplace.features.authentication.application.IssuedTokens;
import com.archimstechcorner.marketplace.features.authentication.application.LoginResult;
import com.archimstechcorner.marketplace.features.authentication.application.LoginService;
import com.archimstechcorner.marketplace.features.authentication.application.PasswordResetService;
import com.archimstechcorner.marketplace.features.authentication.application.RegistrationService;
import com.archimstechcorner.marketplace.features.authentication.application.SessionService;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.security.AuthCookieFactory;
import com.archimstechcorner.marketplace.shared.support.SecureTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Tokens are delivered as {@code Secure}, {@code HttpOnly} cookies;
 * the response bodies never contain a token. Anti-enumeration flows (register, resend, forgot) always
 * return the same acknowledgement regardless of whether the account exists.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final AuthResponses.Message ACK =
            new AuthResponses.Message("If the email is valid, we have sent the requested message.");

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final SessionService sessionService;
    private final PasswordResetService passwordResetService;
    private final AuthCookieFactory cookieFactory;
    private final com.archimstechcorner.marketplace.features.authentication.application.MfaService mfaService;

    public AuthController(RegistrationService registrationService,
                          EmailVerificationService emailVerificationService,
                          LoginService loginService,
                          SessionService sessionService,
                          PasswordResetService passwordResetService,
                          AuthCookieFactory cookieFactory,
                          com.archimstechcorner.marketplace.features.authentication.application.MfaService mfaService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.loginService = loginService;
        this.sessionService = sessionService;
        this.passwordResetService = passwordResetService;
        this.cookieFactory = cookieFactory;
        this.mfaService = mfaService;
    }

    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthResponses.Message registerCustomer(@Valid @RequestBody AuthRequests.RegisterCustomer request) {
        registrationService.registerCustomer(request.email(), request.password(), request.displayName());
        return ACK;
    }

    @PostMapping("/email/verify")
    public AuthResponses.Message verifyEmail(@Valid @RequestBody AuthRequests.TokenOnly request) {
        emailVerificationService.verify(request.token());
        return new AuthResponses.Message("Email verified.");
    }

    @PostMapping("/email/verify/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthResponses.Message resendVerification(@Valid @RequestBody AuthRequests.EmailOnly request) {
        emailVerificationService.resend(request.email());
        return ACK;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequests.Login request,
                                   HttpServletRequest http) {
        LoginResult result = loginService.login(request.email(), request.password(), deviceInfo(http));
        if (result.mfaChallengeToken() != null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new AuthResponses.MfaChallenge(result.mfaChallengeToken()));
        }
        
        AuthResponses.LoginSummary body =
                new AuthResponses.LoginSummary(result.publicId(), result.email(), result.emailVerified());
        return withTokens(result.tokens(), HttpStatus.OK, body);
    }

    @PostMapping("/login/mfa")
    public ResponseEntity<AuthResponses.LoginSummary> loginMfa(@Valid @RequestBody AuthRequests.MfaLogin request,
                                                               HttpServletRequest http) {
        LoginResult result = loginService.loginMfa(request.mfaChallengeToken(), request.code(), deviceInfo(http));
        AuthResponses.LoginSummary body =
                new AuthResponses.LoginSummary(result.publicId(), result.email(), result.emailVerified());
        return withTokens(result.tokens(), HttpStatus.OK, body);
    }

    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @PostMapping("/mfa/totp/enroll")
    public AuthResponses.Message enrollTotp(@org.springframework.security.core.annotation.AuthenticationPrincipal com.archimstechcorner.marketplace.shared.security.AuthenticatedUser user) {
        String uri = mfaService.enrollTotp(user.userId());
        return new AuthResponses.Message(uri);
    }

    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @PostMapping("/mfa/totp/activate")
    public AuthResponses.MfaActivated activateTotp(@org.springframework.security.core.annotation.AuthenticationPrincipal com.archimstechcorner.marketplace.shared.security.AuthenticatedUser user, 
                                              @Valid @RequestBody AuthRequests.MfaActivate request) {
        List<String> recoveryCodes = mfaService.activateTotp(user.userId(), request.code());
        return new AuthResponses.MfaActivated("TOTP activated successfully. Save your recovery codes in a secure location.", recoveryCodes);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<AuthResponses.Message> refresh(HttpServletRequest http) {
        String rawRefresh = cookieFactory.readRefresh(http)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_REVOKED, "No session."));
        IssuedTokens tokens = sessionService.refresh(rawRefresh, deviceInfo(http));
        return withTokens(tokens, HttpStatus.OK, new AuthResponses.Message("Session refreshed."));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponses.Message> logout(HttpServletRequest http) {
        cookieFactory.readRefresh(http).ifPresent(sessionService::logout);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredRefreshCookie().toString())
                .body(new AuthResponses.Message("Logged out."));
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthResponses.Message forgotPassword(@Valid @RequestBody AuthRequests.EmailOnly request) {
        passwordResetService.requestReset(request.email());
        return ACK;
    }

    @PostMapping("/password/reset")
    public AuthResponses.Message resetPassword(@Valid @RequestBody AuthRequests.PasswordReset request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return new AuthResponses.Message("Password updated. Please sign in again.");
    }

    /** Attaches the access + refresh cookies to a response. */
    private <T> ResponseEntity<T> withTokens(IssuedTokens tokens, HttpStatus status, T body) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.accessCookie(tokens.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshCookie(tokens.refreshToken()).toString())
                .body(body);
    }

    /** Builds privacy-safe device context; IP and user-agent are stored hashed only. */
    private DeviceInfo deviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ip = clientIp(request);
        String label = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 120));
        return new DeviceInfo(
                label,
                ip == null ? null : SecureTokens.sha256Hex(ip),
                userAgent == null ? null : SecureTokens.sha256Hex(userAgent));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
