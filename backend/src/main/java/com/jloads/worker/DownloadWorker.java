package com.jloads.worker;

import com.jloads.config.AppProperties;
import com.jloads.exception.ApplicationException;
import com.jloads.exception.DownloadCancelledException;
import com.jloads.exception.DownloadException;
import com.jloads.exception.ErrorCode;
import com.jloads.model.DownloadJob;
import com.jloads.model.DownloadProgress;
import com.jloads.model.StoredFile;
import com.jloads.model.VideoMetadata;
import com.jloads.model.enums.DownloadEventType;
import com.jloads.repository.DownloadJobRepository;
import com.jloads.service.DownloadCommand;
import com.jloads.service.DownloadEventPublisher;
import com.jloads.service.DownloadListener;
import com.jloads.service.DownloadMetrics;
import com.jloads.service.FileStorageService;
import com.jloads.service.VideoAnalysisService;
import com.jloads.service.YtDlpService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Executa o pipeline completo de um job: análise → download → processamento → armazenamento. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadWorker {

    public static final String MDC_JOB_ID = "jobId";
    static final Duration PROGRESS_EVENT_INTERVAL = Duration.ofMillis(500);

    private final DownloadJobRepository repository;
    private final VideoAnalysisService analysisService;
    private final YtDlpService ytDlpService;
    private final FileStorageService storage;
    private final DownloadEventPublisher events;
    private final DownloadMetrics metrics;
    private final AppProperties properties;
    private final Clock clock;

    public void process(UUID jobId) {
        DownloadJob job = repository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        MDC.put(MDC_JOB_ID, jobId.toString());
        try {
            if (!job.markAnalyzing(now())) {
                log.debug("Skipping job in status {}", job.getStatus());
                return;
            }
            save(job, DownloadEventType.JOB_STARTED);
            log.info("Job started: type={} quality={}", job.getType(), job.getQuality());
            execute(job);
        } catch (DownloadCancelledException ex) {
            handleCancelled(job);
        } catch (ApplicationException ex) {
            handleFailure(job, ex.getErrorCode(), ex.getUserMessage(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected failure processing job", ex);
            handleFailure(job, ErrorCode.DOWNLOAD_FAILED, ErrorCode.DOWNLOAD_FAILED.defaultMessage(), ex.getMessage());
        } finally {
            MDC.remove(MDC_JOB_ID);
        }
    }

    private void execute(DownloadJob job) {
        VideoMetadata metadata = analysisService.metadataForDownload(job.getVideoUrl(), job.getType(), job.getQuality());
        job.applyMetadata(metadata);
        if (!job.markDownloading(now())) {
            throw new DownloadCancelledException("job no longer active before download");
        }
        save(job, DownloadEventType.JOB_PROGRESS);

        if (storage.usedSpaceBytes() >= properties.downloads().maxStorageSize().toBytes()) {
            throw new DownloadException(ErrorCode.STORAGE_FULL, "storage limit reached");
        }
        Path workspace = storage.prepareWorkspace(job.getId());
        DownloadCommand command = new DownloadCommand(
                job.getId(), job.getVideoUrl(), job.getType(), job.getQuality(), workspace);
        ytDlpService.download(command, new JobDownloadListener(job));

        if (job.isTerminal()) {
            throw new DownloadCancelledException("job finished externally during download");
        }
        StoredFile stored = storage.storeCompleted(job.getId(), storage.locateDownloadedMedia(job.getId()), metadata.title());
        if (job.markCompleted(stored.filename(), stored.sizeBytes(), now())) {
            save(job, DownloadEventType.JOB_COMPLETED);
            Duration elapsed = Duration.between(job.getCreatedAt(), now());
            metrics.jobCompleted(job.getType(), elapsed, stored.sizeBytes());
            log.info("Job completed: sizeBytes={} durationMs={}", stored.sizeBytes(), elapsed.toMillis());
        } else {
            storage.deleteJobFiles(job.getId());
        }
    }

    private void handleCancelled(DownloadJob job) {
        storage.deleteJobFiles(job.getId());
        if (job.markCancelled(now())) {
            save(job, DownloadEventType.JOB_CANCELLED);
            metrics.jobCancelled(job.getType());
        }
        log.info("Job cancelled");
    }

    private void handleFailure(DownloadJob job, ErrorCode code, String userMessage, String detail) {
        storage.deleteJobFiles(job.getId());
        if (job.markFailed(code.name(), userMessage, now())) {
            save(job, DownloadEventType.JOB_FAILED);
            metrics.jobFailed(job.getType(), code.name());
            log.warn("Job failed: code={} detail={}", code, detail);
        }
    }

    private void save(DownloadJob job, DownloadEventType eventType) {
        repository.save(job);
        events.publish(eventType, job);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private final class JobDownloadListener implements DownloadListener {

        private final DownloadJob job;
        private Instant lastProgressEvent = Instant.EPOCH;

        private JobDownloadListener(DownloadJob job) {
            this.job = job;
        }

        @Override
        public void onProgress(DownloadProgress progress) {
            Instant now = now();
            if (job.updateProgress(progress, now)
                    && Duration.between(lastProgressEvent, now).compareTo(PROGRESS_EVENT_INTERVAL) >= 0) {
                lastProgressEvent = now;
                save(job, DownloadEventType.JOB_PROGRESS);
            }
        }

        @Override
        public void onProcessingStarted(String postprocessor) {
            if (job.markProcessing(now())) {
                save(job, DownloadEventType.JOB_PROCESSING);
                log.debug("Post-processing started: {}", postprocessor);
            }
        }

        @Override
        public boolean isCancelled() {
            return job.isTerminal();
        }
    }
}
