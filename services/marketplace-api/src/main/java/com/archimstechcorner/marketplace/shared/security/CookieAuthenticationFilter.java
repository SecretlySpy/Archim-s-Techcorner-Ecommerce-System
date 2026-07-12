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

import java.io.IOException;

/**
 * Reads the access-token cookie on each request and, if valid, populates the SecurityContext with the
 * principal and its authorities. Invalid/absent tokens leave the request anonymous; the security chain
 * then decides whether the target endpoint requires authentication.
 */
@Component
public class CookieAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokenService accessTokenService;
    private final AuthCookieFactory cookieFactory;

    public CookieAuthenticationFilter(AccessTokenService accessTokenService, AuthCookieFactory cookieFactory) {
        this.accessTokenService = accessTokenService;
        this.cookieFactory = cookieFactory;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Only set authentication when nothing else already did (idempotent, safe on internal forwards).
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookieFactory.readAccess(request)
                    .flatMap(accessTokenService::parse)
                    .ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(AuthenticatedUser user) {
        var authorities = user.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
