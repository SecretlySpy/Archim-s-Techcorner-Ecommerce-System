package com.archimstechcorner.marketplace.features.users.api;

import com.archimstechcorner.marketplace.features.users.application.CurrentUserService;
import com.archimstechcorner.marketplace.features.users.application.RoleAdminService;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Current-principal and administrative user/role endpoints. Authorization is enforced per method. */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final CurrentUserService currentUserService;
    private final RoleAdminService roleAdminService;

    public UserController(CurrentUserService currentUserService, RoleAdminService roleAdminService) {
        this.currentUserService = currentUserService;
        this.roleAdminService = roleAdminService;
    }

    /** Returns the authenticated principal with its effective roles and permissions. */
    @GetMapping("/users/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return currentUserService.describe(principal.publicId());
    }

    /** Assigns a platform role to a user. Requires the {@code users.role.assign} permission. */
    @PostMapping("/users/{publicId}/roles")
    @PreAuthorize("hasAuthority('users.role.assign')")
    public ResponseEntity<Void> assignRole(@PathVariable UUID publicId,
                                           @Valid @RequestBody AssignRoleRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        roleAdminService.assignRole(publicId, request.roleCode(), request.expiresAt(), actor.userId());
        return ResponseEntity.noContent().build();
    }

    /** Revokes a platform role from a user. Requires the {@code users.role.assign} permission. */
    @DeleteMapping("/users/{publicId}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('users.role.assign')")
    public ResponseEntity<Void> revokeRole(@PathVariable UUID publicId,
                                           @PathVariable String roleCode,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        roleAdminService.revokeRole(publicId, roleCode, actor.userId());
        return ResponseEntity.noContent().build();
    }
}
