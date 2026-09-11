package com.jloads.scheduler;

import com.jloads.config.AppProperties;
import com.jloads.exception.ErrorCode;
import com.jloads.model.DownloadJob;
import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.enums.DownloadEventType;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.process.ProcessRegistry;
import com.jloads.repository.DownloadJobRepository;
import com.jloads.service.DownloadEventPublisher;
import com.jloads.service.DownloadMetrics;
import com.jloads.service.FileStorageService;
import com.jloads.service.QueueService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpeza periódica:
 * <ol>
 *   <li>remove arquivos concluídos com mais de {@code app.cleanup.max-age-minutes};</li>
 *   <li>marca como FAILED jobs abandonados (ativos sem processo e sem atualização) ou presos na fila;</li>
 *   <li>remove registros antigos de jobs finalizados;</li>
 *   <li>remove diretórios órfãos do armazenamento.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    static final Duration STALE_GRACE = Duration.ofMinutes(5);

    private final DownloadJobRepository repository;
    private final FileStorageService storage;
    private final ProcessRegistry processRegistry;
    private final QueueService queueService;
    private final DownloadEventPublisher events;
    private final DownloadMetrics metrics;
    private final AppProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.cleanup.interval}", initialDelayString = "${app.cleanup.interval}")
    public void scheduledCleanup() {
        if (properties.cleanup().enabled()) {
            runCleanup();
        }
    }

    public CleanupReport runCleanup() {
        Instant now = Instant.now(clock);
        AppProperties.Cleanup config = properties.cleanup();
        Instant fileCutoff = now.minus(config.maxAge());
        Instant recordCutoff = now.minus(config.jobRetention());
        Instant staleCutoff = now.minus(properties.downloads().timeout().plus(STALE_GRACE));

        int expired = 0;
        int abandoned = 0;
        int purged = 0;
        Set<UUID> protectedJobs = new HashSet<>();

        for (DownloadJob job : repository.findAll()) {
            DownloadJobSnapshot snapshot = job.snapshot();
            switch (snapshot.status()) {
                case COMPLETED -> {
                    if (!snapshot.fileExpired() && snapshot.completedAt().isBefore(fileCutoff)) {
                        storage.deleteJobFiles(job.getId());
                        if (job.markFileExpired(now)) {
                            repository.save(job);
                            events.publish(DownloadEventType.JOB_EXPIRED, job);
                            expired++;
                        }
                    } else if (!snapshot.fileExpired()) {
                        protectedJobs.add(job.getId());
                    }
                }
                case QUEUED -> {
                    if (snapshot.createdAt().isBefore(fileCutoff)) {
                        queueService.remove(job.getId());
                        if (failAbandoned(job, ErrorCode.QUEUE_TIMEOUT, now)) {
                            abandoned++;
                        }
                    } else {
                        protectedJobs.add(job.getId());
                    }
                }
                case ANALYZING, DOWNLOADING, PROCESSING -> {
                    if (!processRegistry.isRunning(job.getId()) && snapshot.updatedAt().isBefore(staleCutoff)) {
                        if (failAbandoned(job, ErrorCode.JOB_INTERRUPTED, now)) {
                            abandoned++;
                        }
                    } else {
                        protectedJobs.add(job.getId());
                    }
                }
                case FAILED, CANCELLED -> {
                    // sem arquivos a proteger
                }
            }
            DownloadStatus status = job.getStatus();
            if (status.isTerminal() && job.getCompletedAt() != null && job.getCompletedAt().isBefore(recordCutoff)) {
                storage.deleteJobFiles(job.getId());
                repository.delete(job.getId());
                protectedJobs.remove(job.getId());
                purged++;
            }
        }

        int orphans = storage.deleteOrphans(fileCutoff, protectedJobs);
        if (expired > 0) {
            metrics.filesExpired(expired);
        }
        CleanupReport report = new CleanupReport(expired, abandoned, purged, orphans);
        if (report.hasChanges()) {
            log.info("Cleanup finished: {}", report);
        }
        return report;
    }

    private boolean failAbandoned(DownloadJob job, ErrorCode code, Instant now) {
        storage.deleteJobFiles(job.getId());
        if (!job.markFailed(code.name(), code.defaultMessage(), now)) {
            return false;
        }
        repository.save(job);
        events.publish(DownloadEventType.JOB_FAILED, job);
        metrics.jobFailed(job.getType(), code.name());
        log.warn("Abandoned job marked as failed: jobId={} code={}", job.getId(), code);
        return true;
    }

    public record CleanupReport(int expiredFiles, int abandonedJobs, int purgedJobs, int orphanDirectories) {
        boolean hasChanges() {
            return expiredFiles + abandonedJobs + purgedJobs + orphanDirectories > 0;
        }
    }
}
