package com.jloads.service;

import com.jloads.config.AppProperties;
import com.jloads.exception.ErrorCode;
import com.jloads.exception.RequestRejectedException;
import com.jloads.exception.UnsupportedFormatException;
import com.jloads.exception.VideoAnalysisException;
import com.jloads.model.FormatOption;
import com.jloads.model.VideoMetadata;
import com.jloads.model.VideoUrl;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;
import com.jloads.validation.YouTubeUrlValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Análise de URLs: valida, obtém metadados (com cache de curta duração) e calcula as opções de download.
 * A quantidade de processos de análise simultâneos é limitada por um semáforo.
 */
@Slf4j
@Service
public class VideoAnalysisService {

    private static final int MAX_CACHE_ENTRIES = 500;
    private static final long ACQUIRE_TIMEOUT_SECONDS = 15;

    private final YouTubeUrlValidator urlValidator;
    private final YtDlpService ytDlpService;
    private final FormatOptionsResolver optionsResolver;
    private final AppProperties properties;
    private final Clock clock;
    private final Semaphore analysisPermits;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public VideoAnalysisService(YouTubeUrlValidator urlValidator, YtDlpService ytDlpService,
                                FormatOptionsResolver optionsResolver, AppProperties properties, Clock clock) {
        this.urlValidator = urlValidator;
        this.ytDlpService = ytDlpService;
        this.optionsResolver = optionsResolver;
        this.properties = properties;
        this.clock = clock;
        this.analysisPermits = new Semaphore(properties.ytdlp().maxConcurrentAnalysis(), true);
    }

    /** Análise solicitada pelo usuário (limitada por concorrência). */
    public VideoAnalysis analyze(String rawUrl) {
        VideoUrl url = urlValidator.validate(rawUrl);
        VideoMetadata metadata = cached(url);
        if (metadata == null) {
            metadata = fetchWithPermit(url);
        }
        ensureDownloadable(metadata);
        List<FormatOption> options = optionsResolver.resolve(metadata);
        if (options.isEmpty()) {
            throw new VideoAnalysisException(ErrorCode.UNSUPPORTED_FORMAT, "no downloadable formats");
        }
        return new VideoAnalysis(url, metadata, options);
    }

    /** Metadados usados pelo worker ao processar um job (a concorrência já é limitada pela fila). */
    public VideoMetadata metadataForDownload(VideoUrl url, DownloadType type, DownloadQuality quality) {
        VideoMetadata metadata = cached(url);
        if (metadata == null) {
            metadata = ytDlpService.fetchMetadata(url);
            store(url, metadata);
        }
        ensureDownloadable(metadata);
        if (!optionsResolver.supportsType(metadata, type)) {
            throw new UnsupportedFormatException("type not available: " + type + "/" + quality);
        }
        return metadata;
    }

    /** Validação antecipada ao criar um job, quando os metadados já estão em cache. */
    public void validateOptionIfKnown(VideoUrl url, DownloadType type, DownloadQuality quality) {
        VideoMetadata metadata = cached(url);
        if (metadata != null && !optionsResolver.supportsType(metadata, type)) {
            throw new UnsupportedFormatException("type not available: " + type + "/" + quality);
        }
    }

    private VideoMetadata fetchWithPermit(VideoUrl url) {
        boolean acquired = false;
        try {
            acquired = analysisPermits.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RequestRejectedException(ErrorCode.SERVER_BUSY, "no analysis permits available");
            }
            VideoMetadata metadata = ytDlpService.fetchMetadata(url);
            store(url, metadata);
            log.info("Video analyzed: videoId={} formats={}", url.videoId(), metadata.formats().size());
            return metadata;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RequestRejectedException(ErrorCode.SERVER_BUSY, "interrupted waiting for permit");
        } finally {
            if (acquired) {
                analysisPermits.release();
            }
        }
    }

    private void ensureDownloadable(VideoMetadata metadata) {
        if (metadata.live()) {
            throw new VideoAnalysisException(ErrorCode.LIVE_NOT_SUPPORTED, "live content");
        }
        Long duration = metadata.durationSeconds();
        if (duration != null && duration > properties.downloads().maxMediaDuration().toSeconds()) {
            throw new VideoAnalysisException(ErrorCode.MEDIA_TOO_LONG, "duration " + duration + "s over limit");
        }
        if (!metadata.formats().isEmpty() && metadata.formats().stream().allMatch(f -> f.drmProtected())) {
            throw new VideoAnalysisException(ErrorCode.DRM_PROTECTED, "all formats are DRM protected");
        }
    }

    private VideoMetadata cached(VideoUrl url) {
        CacheEntry entry = cache.get(url.videoId());
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now(clock))) {
            cache.remove(url.videoId(), entry);
            return null;
        }
        return entry.metadata();
    }

    private void store(VideoUrl url, VideoMetadata metadata) {
        Instant now = Instant.now(clock);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(url.videoId(), new CacheEntry(metadata, now.plus(properties.ytdlp().analysisCacheTtl())));
    }

    private record CacheEntry(VideoMetadata metadata, Instant expiresAt) {
    }
}
