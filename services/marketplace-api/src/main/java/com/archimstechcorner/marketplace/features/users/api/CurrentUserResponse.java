package com.archimstechcorner.marketplace.features.users.api;

import java.util.List;
import java.util.UUID;

/** Response body for {@code GET /users/me}: the principal plus its effective roles and permissions. */
public record CurrentUserResponse(
        UUID id,
        String email,
        String displayName,
        String status,
        boolean emailVerified,
        List<String> roles,
        List<String> permissions) {
}
