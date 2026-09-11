package com.jloads.model;

import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Job de download. Os campos de identidade são imutáveis; o estado muda apenas por transições válidas
 * (ver {@link DownloadStatus#canTransitionTo}). Todas as mutações são sincronizadas porque o job é
 * alterado pelo worker e pelo cancelamento vindo de requisições HTTP ao mesmo tempo.
 */
public class DownloadJob {

    @Getter
    private final UUID id;
    @Getter
    private final ClientId ownerId;
    @Getter
    private final String requesterKey;
    @Getter
    private final VideoUrl videoUrl;
    @Getter
    private final DownloadType type;
    @Getter
    private final DownloadQuality quality;
    @Getter
    private final Instant createdAt;

    private String title;
    private String channel;
    private String thumbnailUrl;
    private Long durationSeconds;

    private DownloadStatus status = DownloadStatus.QUEUED;
    private int progress;
    private long downloadedBytes;
    private long totalBytes;
    private String speed;
    private String eta;
    private long speedBytesPerSecond;

    private String filename;
    private Long fileSizeBytes;
    private boolean fileExpired;

    private String errorCode;
    private String errorMessage;

    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    private DownloadJob(UUID id, ClientId ownerId, String requesterKey, VideoUrl videoUrl, DownloadType type,
                        DownloadQuality quality, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.requesterKey = Objects.requireNonNull(requesterKey, "requesterKey");
        this.videoUrl = Objects.requireNonNull(videoUrl, "videoUrl");
        this.type = Objects.requireNonNull(type, "type");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.thumbnailUrl = videoUrl.fallbackThumbnailUrl();
    }

    public static DownloadJob create(ClientId ownerId, String requesterKey, VideoUrl videoUrl, DownloadType type,
                                     DownloadQuality quality, Instant now) {
        return new DownloadJob(UUID.randomUUID(), ownerId, requesterKey, videoUrl, type, quality, now);
    }

    public synchronized DownloadStatus getStatus() {
        return status;
    }

    public synchronized boolean isTerminal() {
        return status.isTerminal();
    }

    public synchronized Instant getUpdatedAt() {
        return updatedAt;
    }

    public synchronized Instant getCompletedAt() {
        return completedAt;
    }

    public synchronized String getTitle() {
        return title;
    }

    public synchronized void applyMetadata(VideoMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        this.title = metadata.title();
        this.channel = metadata.channel();
        this.durationSeconds = metadata.durationSeconds();
        if (metadata.thumbnailUrl() != null) {
            this.thumbnailUrl = metadata.thumbnailUrl();
        }
    }

    public synchronized boolean markAnalyzing(Instant now) {
        if (!transitionTo(DownloadStatus.ANALYZING, now)) {
            return false;
        }
        this.startedAt = now;
        return true;
    }

    public synchronized boolean markDownloading(Instant now) {
        return transitionTo(DownloadStatus.DOWNLOADING, now);
    }

    public synchronized boolean updateProgress(DownloadProgress value, Instant now) {
        Objects.requireNonNull(value, "value");
        if (status != DownloadStatus.DOWNLOADING) {
            return false;
        }
        this.progress = value.percentage();
        this.downloadedBytes = value.downloadedBytes();
        this.totalBytes = value.totalBytes();
        this.speed = value.speed();
        this.eta = value.eta();
        this.speedBytesPerSecond = value.speedBytesPerSecond();
        this.updatedAt = now;
        return true;
    }

    public synchronized boolean markProcessing(Instant now) {
        if (!transitionTo(DownloadStatus.PROCESSING, now)) {
            return false;
        }
        this.progress = 100;
        clearSpeed();
        return true;
    }

    public synchronized boolean markCompleted(String storedFilename, long sizeBytes, Instant now) {
        Objects.requireNonNull(storedFilename, "storedFilename");
        if (!transitionTo(DownloadStatus.COMPLETED, now)) {
            return false;
        }
        this.filename = storedFilename;
        this.fileSizeBytes = sizeBytes;
        this.progress = 100;
        this.downloadedBytes = Math.max(downloadedBytes, sizeBytes);
        this.totalBytes = Math.max(totalBytes, downloadedBytes);
        clearSpeed();
        this.completedAt = now;
        return true;
    }

    public synchronized boolean markFailed(String code, String userMessage, Instant now) {
        if (!transitionTo(DownloadStatus.FAILED, now)) {
            return false;
        }
        this.errorCode = code;
        this.errorMessage = userMessage;
        finish(now);
        return true;
    }

    public synchronized boolean markCancelled(Instant now) {
        if (!transitionTo(DownloadStatus.CANCELLED, now)) {
            return false;
        }
        finish(now);
        return true;
    }

    /** Indica que o arquivo concluído foi removido pela limpeza automática. */
    public synchronized boolean markFileExpired(Instant now) {
        if (status != DownloadStatus.COMPLETED || fileExpired) {
            return false;
        }
        this.fileExpired = true;
        this.updatedAt = now;
        return true;
    }

    public synchronized DownloadJobSnapshot snapshot() {
        return new DownloadJobSnapshot(
                id, videoUrl.canonicalUrl(), videoUrl.videoId(), title, channel, thumbnailUrl, durationSeconds,
                type, quality, status, progress, downloadedBytes, totalBytes, speed, eta, speedBytesPerSecond,
                filename, fileSizeBytes, fileExpired, errorCode, errorMessage,
                createdAt, startedAt, completedAt, updatedAt);
    }

    private boolean transitionTo(DownloadStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        this.status = target;
        this.updatedAt = now;
        return true;
    }

    private void finish(Instant now) {
        clearSpeed();
        this.completedAt = now;
    }

    private void clearSpeed() {
        this.speed = null;
        this.eta = null;
        this.speedBytesPerSecond = 0;
    }
}
