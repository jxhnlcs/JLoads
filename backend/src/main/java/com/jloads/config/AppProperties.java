package com.jloads.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração tipada da aplicação. Todos os valores podem ser sobrescritos por variáveis de ambiente
 * (ver application.yml).
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull YtDlp ytdlp,
        @Valid @NotNull Ffmpeg ffmpeg,
        @Valid @NotNull Downloads downloads,
        @Valid @NotNull Cleanup cleanup,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Cors cors) {

    public record YtDlp(
            @NotBlank String executable,
            @Pattern(regexp = "^$|^(deno|node|quickjs|bun)(:.+)?$", message = "runtime JS não suportado")
            String jsRuntime,
            @NotNull Duration metadataTimeout,
            @Min(5) @Max(300) int socketTimeoutSeconds,
            @Min(0) @Max(10) int retries,
            @Min(1) @Max(64) int maxConcurrentAnalysis,
            @NotNull Duration analysisCacheTtl) {
    }

    public record Ffmpeg(String executable) {
    }

    public record Downloads(
            @NotBlank String directory,
            @Min(1) @Max(32) int maxConcurrent,
            @Min(1) @Max(10_000) int maxQueueSize,
            @Min(1) @Max(1_000) int maxActiveJobsPerClient,
            @Min(1) @Max(50) int maxBatchSize,
            @NotNull DataSize maxFileSize,
            @NotNull DataSize maxStorageSize,
            @NotNull Duration timeout,
            @NotNull Duration maxMediaDuration,
            @Pattern(regexp = "^$|^\\d+(\\.\\d+)?[KMG]?$", message = "limit-rate inválido (ex.: 500K, 5M)")
            String limitRate) {
    }

    public record Cleanup(
            boolean enabled,
            @Min(1) int maxAgeMinutes,
            @Min(1) int jobRetentionMinutes,
            @NotNull Duration interval) {

        public Duration maxAge() {
            return Duration.ofMinutes(maxAgeMinutes);
        }

        public Duration jobRetention() {
            return Duration.ofMinutes(Math.max(jobRetentionMinutes, maxAgeMinutes));
        }
    }

    public record RateLimit(
            boolean enabled,
            @Min(1) int apiRequestsPerMinute,
            @Min(1) int analyzeRequestsPerMinute,
            @Min(1) int downloadRequestsPerMinute) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
