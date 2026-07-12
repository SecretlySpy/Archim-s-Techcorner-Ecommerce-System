package com.archimstechcorner.marketplace.features.users.api;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Request body for assigning a role. {@code expiresAt} is optional (null = no expiry). */
public record AssignRoleRequest(
        @NotBlank String roleCode,
        Instant expiresAt) {
}
