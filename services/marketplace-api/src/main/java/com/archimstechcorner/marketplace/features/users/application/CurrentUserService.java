package com.archimstechcorner.marketplace.features.users.application;

import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerProfileRepository;
import com.archimstechcorner.marketplace.features.users.api.CurrentUserResponse;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Assembles the {@code GET /users/me} view from the authoritative user record and its authorities. */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AuthorityService authorityService;

    public CurrentUserService(UserRepository userRepository,
                              CustomerProfileRepository customerProfileRepository,
                              AuthorityService authorityService) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.authorityService = authorityService;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse describe(UUID publicId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
        UserAuthorities authorities = authorityService.resolve(user.getId());
        String displayName = customerProfileRepository.findByUserId(user.getId())
                .map(profile -> profile.getDisplayName())
                .orElse(null);
        return new CurrentUserResponse(
                user.getPublicId(),
                user.getEmailDisplay(),
                displayName,
                user.getStatus().name(),
                user.isEmailVerified(),
                authorities.roleCodes(),
                authorities.permissionCodes());
    }
}
