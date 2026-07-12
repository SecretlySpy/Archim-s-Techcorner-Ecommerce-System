package com.archimstechcorner.marketplace.shared.security;

import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.error.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF defense-in-depth for cookie-authenticated requests. Because auth tokens live in cookies the
 * browser sends automatically, state-changing requests must originate from an allow-listed origin.
 * Combined with {@code SameSite=Lax}, this blocks cross-site form/fetch attacks. Requests with no
 * {@code Origin} header (server-to-server, tests) are not browser-initiated and are allowed through.
 *
 * <p>A full double-submit CSRF token is the next hardening step (auth design §7).
 */
@Component
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuthProperties properties;
    private final JsonErrorWriter errorWriter;

    public OriginCheckFilter(AuthProperties properties, JsonErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (MUTATING.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin != null && !properties.getAllowedOrigins().contains(origin)) {
                errorWriter.write(response, ErrorCode.FORBIDDEN, "Origin not allowed.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
