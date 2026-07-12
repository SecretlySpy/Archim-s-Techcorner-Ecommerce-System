package com.archimstechcorner.marketplace.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes returned to clients. The string name is the API contract
 * ({@code error.code}); the {@link HttpStatus} is the transport status. Mirrors the error table in
 * documentation/modules/authentication-rbac.md §8.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    MFA_REQUIRED(HttpStatus.UNAUTHORIZED),
    MFA_INVALID(HttpStatus.UNAUTHORIZED),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
