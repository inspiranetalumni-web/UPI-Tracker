package com.upitracker.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    public Bucket apiBucket() {
        /*
         * ⚠️ CRITICAL ARCHITECTURAL WARNING (5-10 YEAR SCALABILITY) ⚠️
         *
         * This Bucket4j implementation uses an IN-MEMORY token bucket. 
         * If you deploy this backend to multiple instances (e.g., behind an AWS Application 
         * Load Balancer, Vercel, Railway, etc.), the rate limit will NOT synchronize across servers.
         * An attacker could spam the load balancer and easily bypass the limit.
         *
         * BEFORE HORIZONTAL SCALING:
         * You MUST migrate this to a distributed cache.
         * Replace this in-memory Bucket with the Bucket4j JCache/Redis integration:
         * 1. Add dependency: 'com.bucket4j:bucket4j-redis'
         * 2. Configure a RedissonClient
         * 3. Return RedissonBasedProxyManager.builder().build(redissonClient).builder().build(key, config)
         */
         
        // Limit: 100 requests per minute per server instance
        Bandwidth limit = Bandwidth.builder()
                .capacity(100)
                .refillGreedy(100, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
