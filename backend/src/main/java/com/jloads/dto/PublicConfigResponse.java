package com.jloads.dto;

import com.jloads.config.AppProperties;

/** Limites de uso expostos ao frontend (validação e painel de limitações). Nada sensível. */
public record PublicConfigResponse(
        int maxBatchSize,
        int maxActiveJobsPerClient,
        int maxConcurrentDownloads,
        long maxFileSizeBytes,
        long maxMediaDurationSeconds,
        int fileRetentionMinutes) {

    public static PublicConfigResponse from(AppProperties properties) {
        AppProperties.Downloads downloads = properties.downloads();
        return new PublicConfigResponse(
                downloads.maxBatchSize(),
                downloads.maxActiveJobsPerClient(),
                downloads.maxConcurrent(),
                downloads.maxFileSize().toBytes(),
                downloads.maxMediaDuration().toSeconds(),
                properties.cleanup().maxAgeMinutes());
    }
}
