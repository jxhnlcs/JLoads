package com.jloads.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter em memória no algoritmo token bucket, uma instância por categoria de endpoint. Pode ser trocado
 * por uma implementação distribuída (ex.: Redis) sem alterar o filtro.
 */
public class RateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final double capacity;
    private final double refillPerNano;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int requestsPerMinute, Clock clock) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("requestsPerMinute must be positive");
        }
        this.capacity = requestsPerMinute;
        this.refillPerNano = requestsPerMinute / (double) Duration.ofMinutes(1).toNanos();
        this.clock = clock;
    }

    public Decision tryAcquire(String key) {
        if (buckets.size() > MAX_TRACKED_KEYS) {
            evictFullBuckets();
        }
        long now = nowNanos();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (bucket) {
            bucket.refill(now, capacity, refillPerNano);
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return new Decision(true, 0);
            }
            long nanosUntilToken = (long) Math.ceil((1 - bucket.tokens) / refillPerNano);
            return new Decision(false, Math.max(1, (long) Math.ceil(nanosUntilToken / 1_000_000_000.0)));
        }
    }

    /**
     * Remove buckets completamente recarregados. Um bucket cheio é equivalente a um bucket novo, então a remoção
     * não altera o comportamento — apenas limita o uso de memória.
     */
    public void evictFullBuckets() {
        long now = nowNanos();
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                bucket.refill(now, capacity, refillPerNano);
                return bucket.tokens >= capacity;
            }
        });
    }

    int trackedKeys() {
        return buckets.size();
    }

    private long nowNanos() {
        Instant instant = clock.instant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        private Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        private void refill(long now, double capacity, double refillPerNano) {
            if (now > lastRefill) {
                tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
                lastRefill = now;
            }
        }
    }
}
