package com.archimstechcorner.marketplace.features.users.application;

import java.util.List;

/**
 * A user's resolved role codes and permission codes.
 *
 * <p>{@link #grantedAuthorityStrings()} renders the combined authority list used in the access token
 * and SecurityContext: role codes prefixed with {@code ROLE_} (so {@code hasRole('ADMIN')} works) plus
 * bare permission codes (so {@code hasAuthority('users.role.assign')} works).
 */
public record UserAuthorities(List<String> roleCodes, List<String> permissionCodes) {

    public List<String> grantedAuthorityStrings() {
        List<String> authorities = new java.util.ArrayList<>();
        roleCodes.forEach(code -> authorities.add("ROLE_" + code));
        authorities.addAll(permissionCodes);
        return List.copyOf(authorities);
    }
}
