package com.upitracker.backend.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final Bucket globalBucket;
    private final Supplier<Bucket> userBucketFactory;
    private final Supplier<Bucket> insightsBucketFactory;

    // Per-user buckets — keyed by userId (Firebase UID, which is unique per user)
    private final ConcurrentHashMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> insightsBuckets = new ConcurrentHashMap<>();
    // Track last access time for eviction of idle user buckets (10 minutes)
    private final ConcurrentHashMap<String, Long> lastAccessTime = new ConcurrentHashMap<>();
    private static final long EVICTION_TTL_MS = 10L * 60 * 1000;

    @Autowired
    public RateLimiterFilter(
            @Qualifier("globalBucket") Bucket globalBucket,
            @Qualifier("userBucketFactory") Supplier<Bucket> userBucketFactory,
            @Qualifier("insightsBucketFactory") Supplier<Bucket> insightsBucketFactory) {
        this.globalBucket = globalBucket;
        this.userBucketFactory = userBucketFactory;
        this.insightsBucketFactory = insightsBucketFactory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Global DDoS protection
        if (!globalBucket.tryConsume(1)) {
            sendError(response, "Server is under high load. Please try again in a moment.");
            return;
        }

        // 2. Per-user rate limiting (uses Firebase UID — unique per user)
        String userId = extractAuthenticatedUserId();
        if (userId != null) {
            evictStaleUserBuckets();

            // Per-user general limit: 60 req/min
            Bucket userBucket = userBuckets.computeIfAbsent(userId, k -> userBucketFactory.get());
            lastAccessTime.put(userId, System.currentTimeMillis());

            if (!userBucket.tryConsume(1)) {
                sendError(response, "Too many requests. Please slow down and try again in a moment.");
                return;
            }

            // Special stricter limit for the AI insights endpoint (Gemini quota protection)
            if (path.contains("/insights")) {
                Bucket insightsBucket = insightsBuckets.computeIfAbsent(userId, k -> insightsBucketFactory.get());
                if (!insightsBucket.tryConsume(1)) {
                    sendError(response, "AI insights rate limit reached. Insights are cached for 1 hour — please wait before requesting again.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractAuthenticatedUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName(); // Firebase UID set by JwtAuthenticationFilter
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void evictStaleUserBuckets() {
        long now = System.currentTimeMillis();
        lastAccessTime.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > EVICTION_TTL_MS) {
                userBuckets.remove(entry.getKey());
                insightsBuckets.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}

