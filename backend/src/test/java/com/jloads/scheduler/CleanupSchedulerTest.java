package com.jloads.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jloads.model.ClientId;
import com.jloads.model.DownloadJob;
import com.jloads.model.DownloadProgress;
import com.jloads.model.VideoUrl;
import com.jloads.model.enums.DownloadEventType;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import com.jloads.process.ProcessRegistry;
import com.jloads.repository.InMemoryDownloadJobRepository;
import com.jloads.service.DownloadEventPublisher;
import com.jloads.service.DownloadMetrics;
import com.jloads.service.FileStorageService;
import com.jloads.service.QueueService;
import com.jloads.support.MutableClock;
import com.jloads.support.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CleanupSchedulerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InMemoryDownloadJobRepository repository = new InMemoryDownloadJobRepository();
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ProcessRegistry processRegistry = mock(ProcessRegistry.class);
    private final QueueService queueService = mock(QueueService.class);
    private final DownloadEventPublisher events = mock(DownloadEventPublisher.class);
    private CleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        // max-age 30 min, retenção de registros 120 min, timeout de download 5 min
        scheduler = new CleanupScheduler(repository, storage, processRegistry, queueService, events,
                new DownloadMetrics(new SimpleMeterRegistry()), TestProperties.defaults(), clock);
    }

    @Test
    void expiresCompletedFilesAfterMaxAge() {
        DownloadJob job = completedJob();
        clock.advance(Duration.ofMinutes(29));
        assertThat(scheduler.runCleanup().expiredFiles()).isZero();
        verify(storage, never()).deleteJobFiles(job.getId());

        clock.advance(Duration.ofMinutes(2));
        assertThat(scheduler.runCleanup().expiredFiles()).isEqualTo(1);

        verify(storage).deleteJobFiles(job.getId());
        verify(events).publish(DownloadEventType.JOB_EXPIRED, job);
        assertThat(job.snapshot().fileExpired()).isTrue();
        assertThat(job.snapshot().fileAvailable()).isFalse();
    }

    @Test
    void protectsAvailableFilesFromOrphanCleanup() {
        DownloadJob job = completedJob();
        scheduler.runCleanup();
        verify(storage).deleteOrphans(any(), argThat(ids -> ids.contains(job.getId())));
    }

    @Test
    void marksAbandonedActiveJobsAsFailed() {
        DownloadJob job = newJob();
        job.markAnalyzing(clock.instant());
        job.markDownloading(clock.instant());
        when(processRegistry.isRunning(job.getId())).thenReturn(false);

        clock.advance(Duration.ofMinutes(9));
        assertThat(scheduler.runCleanup().abandonedJobs()).isZero();

        clock.advance(Duration.ofMinutes(2));
        assertThat(scheduler.runCleanup().abandonedJobs()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(DownloadStatus.FAILED);
        assertThat(job.snapshot().errorCode()).isEqualTo("JOB_INTERRUPTED");
        verify(events).publish(DownloadEventType.JOB_FAILED, job);
    }

    @Test
    void keepsLongRunningJobsWithLiveProcess() {
        DownloadJob job = newJob();
        job.markAnalyzing(clock.instant());
        job.markDownloading(clock.instant());
        when(processRegistry.isRunning(job.getId())).thenReturn(true);

        clock.advance(Duration.ofHours(1));
        scheduler.runCleanup();

        assertThat(job.getStatus()).isEqualTo(DownloadStatus.DOWNLOADING);
    }

    @Test
    void failsJobsStuckInQueue() {
        DownloadJob job = newJob();
        clock.advance(Duration.ofMinutes(31));

        scheduler.runCleanup();

        verify(queueService).remove(job.getId());
        assertThat(job.snapshot().errorCode()).isEqualTo("QUEUE_TIMEOUT");
    }

    @Test
    void purgesOldFinishedJobRecords() {
        DownloadJob job = newJob();
        job.markCancelled(clock.instant());

        clock.advance(Duration.ofMinutes(121));
        assertThat(scheduler.runCleanup().purgedJobs()).isEqualTo(1);
        assertThat(repository.findById(job.getId())).isEmpty();
        verify(storage).deleteOrphans(any(), eq(java.util.Set.of()));
    }

    private DownloadJob newJob() {
        DownloadJob job = DownloadJob.create(new ClientId(UUID.randomUUID()), "ip", new VideoUrl("jNQXAC9IVRw"),
                DownloadType.AUDIO, DownloadQuality.BEST, clock.instant());
        return repository.save(job);
    }

    private DownloadJob completedJob() {
        DownloadJob job = newJob();
        job.markAnalyzing(clock.instant());
        job.markDownloading(clock.instant());
        job.updateProgress(new DownloadProgress(50, 5, 10, null, null, 0), clock.instant());
        job.markCompleted("a.mp3", 10, clock.instant());
        return job;
    }
}
