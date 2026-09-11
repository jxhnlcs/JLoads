package com.jloads.service;

import com.jloads.model.enums.DownloadType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Métricas de negócio expostas via Actuator ({@code /actuator/metrics/jloads.*}). */
@Component
public class DownloadMetrics {

    private final MeterRegistry registry;

    public DownloadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void jobCreated(DownloadType type) {
        counter("jloads.jobs.created", "type", type.name()).increment();
    }

    public void jobCompleted(DownloadType type, Duration elapsed, long sizeBytes) {
        counter("jloads.jobs.completed", "type", type.name()).increment();
        Timer.builder("jloads.jobs.duration").tag("type", type.name()).register(registry).record(elapsed);
        counter("jloads.bytes.delivered", "type", type.name()).increment(sizeBytes);
    }

    public void jobFailed(DownloadType type, String errorCode) {
        counter("jloads.jobs.failed", "type", type.name(), "code", errorCode).increment();
    }

    public void jobCancelled(DownloadType type) {
        counter("jloads.jobs.cancelled", "type", type.name()).increment();
    }

    public void filesExpired(int count) {
        counter("jloads.cleanup.expired").increment(count);
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }
}
