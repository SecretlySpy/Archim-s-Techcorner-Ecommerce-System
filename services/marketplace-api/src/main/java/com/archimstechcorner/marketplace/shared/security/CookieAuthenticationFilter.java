package com.archimstechcorner.marketplace.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;

/**
 * Reads the access-token cookie on each request and, if valid, populates the SecurityContext with the
 * principal and its authorities. Validates that the user's security version matches and account is active,
 * rejecting revoked, locked, or stale access tokens.
 */
@Component
public class CookieAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokenService accessTokenService;
    private final AuthCookieFactory cookieFactory;
    private final UserRepository userRepository;

    public CookieAuthenticationFilter(AccessTokenService accessTokenService,
                                    AuthCookieFactory cookieFactory,
                                    UserRepository userRepository) {
        this.accessTokenService = accessTokenService;
        this.cookieFactory = cookieFactory;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Only set authentication when nothing else already did (idempotent, safe on internal forwards).
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookieFactory.readAccess(request)
                    .flatMap(accessTokenService::parse)
                    .filter(this::isUserSecurityValid)
                    .ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isUserSecurityValid(AuthenticatedUser tokenUser) {
        return userRepository.findById(tokenUser.userId())
                .filter(u -> u.getSecurityVersion() == tokenUser.securityVersion())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE || u.getStatus() == UserStatus.PENDING_VERIFICATION)
                .filter(u -> !u.isLockedAt(Instant.now()))
                .isPresent();
    }

    private void authenticate(AuthenticatedUser user) {
        var authorities = user.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
