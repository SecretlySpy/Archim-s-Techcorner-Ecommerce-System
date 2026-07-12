package com.archimstechcorner.marketplace.features.users.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.users.domain.model.Role;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserRole;
import com.archimstechcorner.marketplace.features.users.domain.repository.RoleRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRoleRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Administrative role management (permission {@code users.role.assign}). Assignments are attributed and
 * audited; revocations keep an immutable history row (never a hard delete) per the RBAC design.
 */
@Service
public class RoleAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;
    private final Clock clock;

    public RoleAdminService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            UserRoleRepository userRoleRepository,
                            AuditService auditService,
                            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void assignRole(UUID targetPublicId, String roleCode, Instant expiresAt, Long actorUserId) {
        User target = requireUser(targetPublicId);
        Role role = requireRole(roleCode);

        // Idempotent: a re-issued assignment for an already-active (user, role) is a no-op, not a duplicate.
        boolean alreadyActive = userRoleRepository
                .findByUserIdAndRoleIdAndActiveMarker(target.getId(), role.getId(), UserRole.ACTIVE)
                .isPresent();
        if (alreadyActive) {
            return;
        }

        UserRole assignment = UserRole.create();
        assignment.setUserId(target.getId());
        assignment.setRoleId(role.getId());
        assignment.setActiveMarker(UserRole.ACTIVE);
        assignment.setGrantedByUserId(actorUserId);
        assignment.setGrantedAt(clock.instant());
        assignment.setExpiresAt(expiresAt);
        userRoleRepository.save(assignment);

        auditService.record("ROLE_ASSIGNED", "USER", targetPublicId.toString(), actorUserId, "role=" + roleCode);
    }

    @Transactional
    public void revokeRole(UUID targetPublicId, String roleCode, Long actorUserId) {
        User target = requireUser(targetPublicId);
        Role role = requireRole(roleCode);

        UserRole active = userRoleRepository
                .findByUserIdAndRoleIdAndActiveMarker(target.getId(), role.getId(), UserRole.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No active assignment for that role."));

        // Move the row to history: clear the active marker so the unique index frees the (user, role) slot.
        active.setActiveMarker(null);
        active.setRevokedByUserId(actorUserId);
        active.setRevokedAt(clock.instant());
        userRoleRepository.save(active);

        auditService.record("ROLE_REVOKED", "USER", targetPublicId.toString(), actorUserId, "role=" + roleCode);
    }

    private User requireUser(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
    }

    private Role requireRole(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found."));
    }
}
