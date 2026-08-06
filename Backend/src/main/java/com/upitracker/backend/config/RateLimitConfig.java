package com.upitracker.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

@Configuration
public class RateLimitConfig {

    /**
     * Global server-wide bucket: 500 requests per minute across all users.
     * Protects against basic DDoS. Per-user limit (below) is the real enforcement.
     *
     * ⚠️ SCALE-OUT WARNING: This is in-memory. For multi-instance deployments,
     * migrate to Bucket4j Redis integration before horizontal scaling.
     */
    @Bean("globalBucket")
    public Bucket globalBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(500)
                        .refillGreedy(500, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Factory for per-user buckets: 60 requests per minute per authenticated user.
     * Ensures one misbehaving user cannot starve other users.
     */
    @Bean("userBucketFactory")
    public Supplier<Bucket> userBucketFactory() {
        return () -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(60)
                        .refillGreedy(60, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Stricter bucket for AI insights endpoint.
     * gemini-2.5-flash free tier: 500 RPD = ~20/hour = ~1 every 3 minutes.
     * Allow max 10 per hour per user (server-side cache handles the rest).
     */
    @Bean("insightsBucketFactory")
    public Supplier<Bucket> insightsBucketFactory() {
        return () -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(10)
                        .refillGreedy(10, Duration.ofHours(1))
                        .build())
                .build();
    }
}

