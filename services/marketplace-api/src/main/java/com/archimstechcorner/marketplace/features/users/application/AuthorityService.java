package com.archimstechcorner.marketplace.features.users.application;

import com.archimstechcorner.marketplace.features.users.domain.model.Role;
import com.archimstechcorner.marketplace.features.users.domain.repository.PermissionRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.RoleRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Resolves the effective roles and permissions of a user for token issuance and authorization. */
@Service
public class AuthorityService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final Clock clock;

    public AuthorityService(UserRoleRepository userRoleRepository,
                            RoleRepository roleRepository,
                            PermissionRepository permissionRepository,
                            Clock clock) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserAuthorities resolve(Long userId) {
        List<Long> roleIds = userRoleRepository.findActiveRoleIds(userId, clock.instant());
        if (roleIds.isEmpty()) {
            return new UserAuthorities(List.of(), List.of());
        }
        List<String> roleCodes = roleRepository.findByIdIn(roleIds).stream()
                .filter(Role::isActive)
                .map(Role::getCode)
                .toList();
        // A permission is granted transitively through any active role the user holds.
        List<String> permissionCodes = permissionRepository.findActiveCodesByRoleIds(roleIds);
        return new UserAuthorities(roleCodes, permissionCodes);
    }
}
