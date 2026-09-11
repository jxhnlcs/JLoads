package com.jloads.dto;

import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import java.time.Instant;
import java.util.UUID;

public record DownloadResponse(
        UUID id,
        String sourceUrl,
        String title,
        String channel,
        String thumbnailUrl,
        Long duration,
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
        boolean fileAvailable,
        boolean fileExpired,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {

    public static DownloadResponse from(DownloadJobSnapshot s) {
        return new DownloadResponse(
                s.id(), s.sourceUrl(), s.title(), s.channel(), s.thumbnailUrl(), s.durationSeconds(),
                s.type(), s.quality(), s.status(), s.progress(), s.downloadedBytes(), s.totalBytes(),
                s.speed(), s.eta(), s.speedBytesPerSecond(), s.filename(), s.fileSizeBytes(), s.fileAvailable(), s.fileExpired(),
                s.errorCode(), s.errorMessage(), s.createdAt(), s.startedAt(), s.completedAt(), s.updatedAt());
    }
}
