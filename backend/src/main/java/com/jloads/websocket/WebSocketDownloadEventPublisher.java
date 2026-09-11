package com.jloads.websocket;

import com.jloads.dto.DownloadEvent;
import com.jloads.model.DownloadJob;
import com.jloads.model.enums.DownloadEventType;
import com.jloads.service.DownloadEventPublisher;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDownloadEventPublisher implements DownloadEventPublisher {

    private final DownloadWebSocketHandler handler;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void publish(DownloadEventType type, DownloadJob job) {
        try {
            DownloadEvent event = DownloadEvent.of(type, job.snapshot(), Instant.now(clock));
            handler.sendToClient(job.getOwnerId(), objectMapper.writeValueAsString(event));
        } catch (RuntimeException ex) {
            // Eventos são best-effort: falhar a entrega nunca pode interromper o download.
            log.warn("Unable to publish {} event: {}", type, ex.getMessage());
        }
    }
}
