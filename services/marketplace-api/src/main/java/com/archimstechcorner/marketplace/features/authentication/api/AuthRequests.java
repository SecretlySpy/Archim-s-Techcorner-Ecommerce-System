package com.archimstechcorner.marketplace.features.authentication.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request bodies for the auth endpoints, grouped as records. Bean Validation runs at the boundary;
 * business rules (uniqueness, token validity) are enforced in the application services.
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record RegisterCustomer(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(min = 2, max = 60) String displayName) {
    }

    public record Login(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password) {
    }

    public record TokenOnly(@NotBlank String token) {
    }

    public record EmailOnly(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record PasswordReset(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record MfaLogin(
            @NotBlank String mfaChallengeToken,
            @NotBlank @Size(min = 6, max = 6) String code) {
    }

    public record MfaActivate(
            @NotBlank @Size(min = 6, max = 6) String code) {
    }
}
