package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class SearchRateLimiter {

    private record RateLimitBucket(long windowStart, AtomicInteger count) {}

    private final Map<UUID, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    public boolean isRateLimited(UUID userId, int maxPerMinute) {
        long now = System.currentTimeMillis();
        RateLimitBucket bucket = buckets.compute(userId, (k, existing) -> {
            if (existing == null || now - existing.windowStart() > 60_000) {
                return new RateLimitBucket(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return bucket.count().get() > maxPerMinute;
    }
}
