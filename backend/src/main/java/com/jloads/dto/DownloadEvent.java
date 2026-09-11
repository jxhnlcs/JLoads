package com.jloads.dto;

import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.enums.DownloadEventType;
import com.jloads.model.enums.DownloadStatus;
import java.time.Instant;
import java.util.UUID;

/** Mensagem enviada pelo WebSocket. Inclui o estado completo do job para o frontend apenas substituí-lo. */
public record DownloadEvent(
        DownloadEventType event,
        UUID jobId,
        DownloadStatus status,
        int progress,
        long downloadedBytes,
        long totalBytes,
        String speed,
        String eta,
        Instant timestamp,
        DownloadResponse job) {

    public static DownloadEvent of(DownloadEventType type, DownloadJobSnapshot snapshot, Instant timestamp) {
        return new DownloadEvent(type, snapshot.id(), snapshot.status(), snapshot.progress(),
                snapshot.downloadedBytes(), snapshot.totalBytes(), snapshot.speed(), snapshot.eta(),
                timestamp, DownloadResponse.from(snapshot));
    }
}
