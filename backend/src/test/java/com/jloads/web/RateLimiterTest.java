package com.jloads.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jloads.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        RateLimiter limiter = new RateLimiter(3, clock);

        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();

        RateLimiter.Decision rejected = limiter.tryAcquire("ip");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 20L);
    }

    @Test
    void refillsOverTime() {
        RateLimiter limiter = new RateLimiter(60, clock);
        for (int i = 0; i < 60; i++) {
            limiter.tryAcquire("ip");
        }
        assertThat(limiter.tryAcquire("ip").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isFalse();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, clock);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void evictsOnlyFullyRefilledBuckets() {
        RateLimiter limiter = new RateLimiter(10, clock);
        limiter.tryAcquire("a");
        limiter.evictFullBuckets();
        assertThat(limiter.trackedKeys()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        limiter.evictFullBuckets();
        assertThat(limiter.trackedKeys()).isZero();
    }
}
