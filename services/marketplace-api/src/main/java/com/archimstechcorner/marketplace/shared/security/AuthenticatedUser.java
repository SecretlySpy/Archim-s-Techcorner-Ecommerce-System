package com.archimstechcorner.marketplace.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal placed in the SecurityContext for each request. Built from the access
 * token's claims (no database hit on the hot path). {@code authorities} holds both role authorities
 * ({@code ROLE_ADMIN}) and permission authorities ({@code users.role.assign}).
 *
 * <p>Controllers access it via {@code @AuthenticationPrincipal AuthenticatedUser}.
 */
public record AuthenticatedUser(
        Long userId,
        UUID publicId,
        String email,
        int securityVersion,
        List<String> authorities) {
}
