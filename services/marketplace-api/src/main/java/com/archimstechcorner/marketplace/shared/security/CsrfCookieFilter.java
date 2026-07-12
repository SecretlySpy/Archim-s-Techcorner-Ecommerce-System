package com.archimstechcorner.marketplace.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures that the CsrfToken is loaded on every request so that the Double-Submit Cookie
 * (XSRF-TOKEN) is successfully sent back to the frontend SPA.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // By calling getToken(), we force the CsrfTokenRepository to write the cookie to the response
            csrfToken.getToken();
        }
        
        filterChain.doFilter(request, response);
    }
}
