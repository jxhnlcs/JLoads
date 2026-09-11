package com.jloads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jloads.exception.ApplicationException;
import com.jloads.exception.ErrorCode;
import com.jloads.exception.FileNotFoundException;
import com.jloads.exception.InvalidUrlException;
import com.jloads.exception.JobNotFoundException;
import com.jloads.exception.RequestRejectedException;
import com.jloads.model.ClientId;
import com.jloads.model.DownloadJob;
import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.enums.DownloadEventType;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import com.jloads.process.ProcessRegistry;
import com.jloads.process.RunningProcess;
import com.jloads.repository.InMemoryDownloadJobRepository;
import com.jloads.support.TestProperties;
import com.jloads.validation.YouTubeUrlValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloadServiceTest {

    private static final String URL = "https://youtu.be/jNQXAC9IVRw";

    @Mock
    VideoAnalysisService analysisService;
    @Mock
    QueueService queueService;
    @Mock
    ProcessRegistry processRegistry;
    @Mock
    FileStorageService storage;
    @Mock
    DownloadEventPublisher events;

    private InMemoryDownloadJobRepository repository;
    private DownloadService service;
    private final ClientId owner = new ClientId(UUID.randomUUID());
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        lenient().when(queueService.remainingCapacity()).thenReturn(100);
        repository = new InMemoryDownloadJobRepository();
        service = new DownloadService(repository, new YouTubeUrlValidator(), analysisService, queueService,
                processRegistry, storage, events, new DownloadMetrics(new SimpleMeterRegistry()),
                TestProperties.create("storage", 2, 10, 2), clock);
    }

    @Test
    void createPersistsQueuesAndPublishes() {
        DownloadJobSnapshot snapshot = service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "1.1.1.1");

        assertThat(snapshot.status()).isEqualTo(DownloadStatus.QUEUED);
        assertThat(repository.findById(snapshot.id())).isPresent();
        verify(queueService).enqueue(snapshot.id());
        verify(events).publish(eq(DownloadEventType.JOB_QUEUED), any(DownloadJob.class));
    }

    @Test
    void createRejectsInvalidUrlWithoutSideEffects() {
        assertThatThrownBy(() -> service.create("https://evil.com", DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip"))
                .isInstanceOf(InvalidUrlException.class);
        assertThat(repository.findAll()).isEmpty();
        verify(queueService, never()).enqueue(any());
    }

    @Test
    void createEnforcesActiveJobLimitPerRequester() {
        service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip");
        service.create(URL, DownloadType.VIDEO, DownloadQuality.BEST, owner, "ip");

        assertThatThrownBy(() -> service.create(URL, DownloadType.AUDIO, DownloadQuality.LOW, owner, "ip"))
                .isInstanceOf(RequestRejectedException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_ACTIVE_JOBS);

        service.create(URL, DownloadType.AUDIO, DownloadQuality.LOW, owner, "other-ip");
    }

    @Test
    void createRollsBackWhenQueueIsFull() {
        doThrow(new RequestRejectedException(ErrorCode.QUEUE_FULL, "full")).when(queueService).enqueue(any());

        assertThatThrownBy(() -> service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip"))
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_FULL);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void cancelQueuedJobRemovesFromQueue() {
        DownloadJobSnapshot created = service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip");
        when(queueService.remove(created.id())).thenReturn(true);

        DownloadJobSnapshot cancelled = service.cancel(created.id(), owner);

        assertThat(cancelled.status()).isEqualTo(DownloadStatus.CANCELLED);
        verify(processRegistry).terminate(created.id(), RunningProcess.TerminationReason.CANCELLED);
        verify(events).publish(eq(DownloadEventType.JOB_CANCELLED), any(DownloadJob.class));
    }

    @Test
    void cancelRunningJobTerminatesProcess() {
        DownloadJobSnapshot created = service.create(URL, DownloadType.VIDEO, DownloadQuality.BEST, owner, "ip");
        DownloadJob job = repository.findById(created.id()).orElseThrow();
        job.markAnalyzing(Instant.now(clock));
        job.markDownloading(Instant.now(clock));
        when(processRegistry.terminate(created.id(), RunningProcess.TerminationReason.CANCELLED)).thenReturn(true);

        service.cancel(created.id(), owner);

        verify(processRegistry).terminate(created.id(), RunningProcess.TerminationReason.CANCELLED);
        assertThat(job.getStatus()).isEqualTo(DownloadStatus.CANCELLED);
    }

    @Test
    void cancelValidatesOwnershipAndState() {
        DownloadJobSnapshot created = service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip");

        assertThatThrownBy(() -> service.cancel(created.id(), new ClientId(UUID.randomUUID())))
                .isInstanceOf(JobNotFoundException.class);

        service.cancel(created.id(), owner);
        assertThatThrownBy(() -> service.cancel(created.id(), owner))
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.JOB_NOT_CANCELLABLE);
    }

    @Test
    void completedFileRequiresCompletedAndNotExpiredJob() {
        DownloadJobSnapshot created = service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip");
        assertThatThrownBy(() -> service.getCompletedFile(created.id())).isInstanceOf(FileNotFoundException.class);

        DownloadJob job = repository.findById(created.id()).orElseThrow();
        job.markAnalyzing(Instant.now(clock));
        job.markDownloading(Instant.now(clock));
        job.markCompleted("a.mp3", 10, Instant.now(clock));
        service.getCompletedFile(created.id());
        verify(storage).loadCompleted(created.id(), "a.mp3");

        job.markFileExpired(Instant.now(clock));
        assertThatThrownBy(() -> service.getCompletedFile(created.id()))
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_EXPIRED);
    }

    @Test
    void listReturnsOnlyOwnerJobs() {
        service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip");
        service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, new ClientId(UUID.randomUUID()), "ip2");

        assertThat(service.list(owner)).hasSize(1);
    }

    @Test
    void createBatchCreatesOneJobPerUniqueVideo() {
        List<DownloadJobSnapshot> jobs = service.createBatch(List.of(
                        "https://youtu.be/jNQXAC9IVRw",
                        "https://www.youtube.com/watch?v=jNQXAC9IVRw",
                        "   ",
                        "https://youtu.be/aaaaaaaaaaa"),
                DownloadType.AUDIO, DownloadQuality.HIGH, owner, "ip");

        assertThat(jobs).extracting(DownloadJobSnapshot::videoId).containsExactly("jNQXAC9IVRw", "aaaaaaaaaaa");
        assertThat(jobs).allMatch(job -> job.status() == DownloadStatus.QUEUED && job.quality() == DownloadQuality.HIGH);
        verify(queueService, times(2)).enqueue(any());
        verify(events, times(2)).publish(eq(DownloadEventType.JOB_QUEUED), any(DownloadJob.class));
    }

    @Test
    void createBatchIsAllOrNothingOnInvalidLink() {
        assertThatThrownBy(() -> service.createBatch(
                List.of("https://youtu.be/jNQXAC9IVRw", "https://evil.com/watch?v=jNQXAC9IVRw"),
                DownloadType.VIDEO, DownloadQuality.BEST, owner, "ip"))
                .isInstanceOf(InvalidUrlException.class)
                .extracting(ex -> ((ApplicationException) ex).getUserMessage())
                .asString()
                .contains("link 2");
        assertThat(repository.findAll()).isEmpty();
        verify(queueService, never()).enqueue(any());
    }

    @Test
    void createBatchRejectsMoreLinksThanAllowed() {
        List<String> urls = List.of("https://youtu.be/aaaaaaaaaa1", "https://youtu.be/aaaaaaaaaa2",
                "https://youtu.be/aaaaaaaaaa3", "https://youtu.be/aaaaaaaaaa4", "https://youtu.be/aaaaaaaaaa5",
                "https://youtu.be/aaaaaaaaaa6");

        assertThatThrownBy(() -> service.createBatch(urls, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip"))
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BATCH_TOO_LARGE);
    }

    @Test
    void createBatchReportsRemainingActiveSlots() {
        service.create(URL, DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip");

        assertThatThrownBy(() -> service.createBatch(
                List.of("https://youtu.be/aaaaaaaaaaa", "https://youtu.be/bbbbbbbbbbb"),
                DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip"))
                .isInstanceOf(RequestRejectedException.class)
                .extracting(ex -> ((ApplicationException) ex).getUserMessage())
                .asString()
                .contains("mais 1");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void createBatchChecksQueueCapacityBeforeCreatingJobs() {
        when(queueService.remainingCapacity()).thenReturn(1);

        assertThatThrownBy(() -> service.createBatch(
                List.of("https://youtu.be/aaaaaaaaaaa", "https://youtu.be/bbbbbbbbbbb"),
                DownloadType.AUDIO, DownloadQuality.BEST, owner, "ip"))
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_FULL);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void getUnknownJobThrows() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(JobNotFoundException.class);
    }
}
