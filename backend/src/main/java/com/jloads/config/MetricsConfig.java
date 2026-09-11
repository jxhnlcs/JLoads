package com.jloads.config;

import com.jloads.process.ProcessRegistry;
import com.jloads.service.QueueService;
import com.jloads.websocket.DownloadWebSocketHandler;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    ApplicationRunner registerGauges(MeterRegistry registry, QueueService queue, ProcessRegistry processes,
                                     DownloadWebSocketHandler webSocket) {
        return args -> {
            Gauge.builder("jloads.queue.size", queue, QueueService::size).register(registry);
            Gauge.builder("jloads.workers.busy", queue, QueueService::busyWorkers).register(registry);
            Gauge.builder("jloads.workers.total", queue, QueueService::workerCount).register(registry);
            Gauge.builder("jloads.processes.running", processes, ProcessRegistry::runningCount).register(registry);
            Gauge.builder("jloads.websocket.sessions", webSocket, DownloadWebSocketHandler::sessionCount).register(registry);
        };
    }
}
