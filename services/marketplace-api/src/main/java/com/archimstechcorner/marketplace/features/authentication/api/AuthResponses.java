package com.archimstechcorner.marketplace.features.authentication.api;

import java.util.UUID;

/** Response bodies for the auth endpoints. */
public final class AuthResponses {

    private AuthResponses() {
    }

    /** Generic acknowledgement (kept deliberately vague for anti-enumeration flows). */
    public record Message(String message) {
    }

    /** Returned on successful login; tokens travel in cookies, not in this body. */
    public record LoginSummary(UUID id, String email, boolean emailVerified) {
    }

    /** Returned when MFA is required to complete login. */
    public record MfaChallenge(String challengeToken) {
    }
}
