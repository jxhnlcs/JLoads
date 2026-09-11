package com.jloads.model;

import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import java.time.Instant;
import java.util.UUID;

/** Visão imutável e consistente de um {@link DownloadJob} em um instante. */
public record DownloadJobSnapshot(
        UUID id,
        String sourceUrl,
        String videoId,
        String title,
        String channel,
        String thumbnailUrl,
        Long durationSeconds,
        DownloadType type,
        DownloadQuality quality,
        DownloadStatus status,
        int progress,
        long downloadedBytes,
        long totalBytes,
        String speed,
        String eta,
        long speedBytesPerSecond,
        String filename,
        Long fileSizeBytes,
        boolean fileExpired,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {

    public boolean fileAvailable() {
        return status == DownloadStatus.COMPLETED && !fileExpired && filename != null;
    }
}
