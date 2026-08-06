package com.upitracker.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Adds production-grade HTTP security headers to every response.
 * Prevents XSS, clickjacking, MIME sniffing, and enforces HTTPS.
 */
@Component
@Order(1) // Run before other filters
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Prevent browsers from MIME-sniffing a response
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Deny embedding this app in an iframe (clickjacking protection)
        response.setHeader("X-Frame-Options", "DENY");

        // Legacy XSS protection header (still useful for older browsers)
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Control what referrer information is sent
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Restrict browser features
        response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");

        // Enforce HTTPS for 1 year (only meaningful when actually running on HTTPS)
        String proto = request.getHeader("X-Forwarded-Proto");
        if ("https".equalsIgnoreCase(request.getScheme()) || "https".equalsIgnoreCase(proto)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        }

        // Prevent sensitive API responses from being cached by browsers/proxies
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }
}
