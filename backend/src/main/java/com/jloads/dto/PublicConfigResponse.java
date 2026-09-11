package com.jloads.dto;

import com.jloads.config.AppProperties;
import com.jloads.process.ExternalBinaries;

/**
 * Limites de uso e situação das ferramentas externas, expostos ao frontend (validação, painel de limitações e aviso
 * de instalação). Nada sensível.
 */
public record PublicConfigResponse(
        int maxBatchSize,
        int maxActiveJobsPerClient,
        int maxConcurrentDownloads,
        long maxFileSizeBytes,
        long maxMediaDurationSeconds,
        int fileRetentionMinutes,
        Dependencies dependencies) {

    /** Quais programas externos foram encontrados ao iniciar. */
    public record Dependencies(boolean ytDlp, boolean ffmpeg, boolean jsRuntime) {
    }

    public static PublicConfigResponse from(AppProperties properties, ExternalBinaries binaries) {
        AppProperties.Downloads downloads = properties.downloads();
        return new PublicConfigResponse(
                downloads.maxBatchSize(),
                downloads.maxActiveJobsPerClient(),
                downloads.maxConcurrent(),
                downloads.maxFileSize().toBytes(),
                downloads.maxMediaDuration().toSeconds(),
                properties.cleanup().maxAgeMinutes(),
                new Dependencies(
                        binaries.ytDlpAvailable(),
                        binaries.ffmpegLocation().isPresent(),
                        !"nenhum".equals(binaries.jsRuntimeName())));
    }
}
