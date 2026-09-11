package com.jloads.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DownloadJobTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private DownloadJob newJob() {
        return DownloadJob.create(new ClientId(UUID.randomUUID()), "127.0.0.1", new VideoUrl("jNQXAC9IVRw"),
                DownloadType.AUDIO, DownloadQuality.BEST, T0);
    }

    @Test
    void startsQueuedWithFallbackThumbnail() {
        DownloadJobSnapshot snapshot = newJob().snapshot();

        assertThat(snapshot.status()).isEqualTo(DownloadStatus.QUEUED);
        assertThat(snapshot.progress()).isZero();
        assertThat(snapshot.thumbnailUrl()).isEqualTo("https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg");
        assertThat(snapshot.sourceUrl()).isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
        assertThat(snapshot.fileAvailable()).isFalse();
    }

    @Test
    void followsHappyPathLifecycle() {
        DownloadJob job = newJob();

        assertThat(job.markAnalyzing(T0.plusSeconds(1))).isTrue();
        job.applyMetadata(new VideoMetadata("jNQXAC9IVRw", "Title", "Channel", 19L, null, false, List.of()));
        assertThat(job.markDownloading(T0.plusSeconds(2))).isTrue();
        assertThat(job.updateProgress(new DownloadProgress(57, 570, 1000, "1.0 MB/s", "00:01", 1_048_576), T0.plusSeconds(3))).isTrue();
        assertThat(job.markProcessing(T0.plusSeconds(4))).isTrue();
        assertThat(job.markCompleted("Title.mp3", 1234, T0.plusSeconds(5))).isTrue();

        DownloadJobSnapshot snapshot = job.snapshot();
        assertThat(snapshot.status()).isEqualTo(DownloadStatus.COMPLETED);
        assertThat(snapshot.progress()).isEqualTo(100);
        assertThat(snapshot.title()).isEqualTo("Title");
        assertThat(snapshot.speed()).isNull();
        assertThat(snapshot.startedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(snapshot.completedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(snapshot.fileAvailable()).isTrue();
    }

    @Test
    void rejectsSkippingStates() {
        DownloadJob job = newJob();

        assertThat(job.markDownloading(T0)).isFalse();
        assertThat(job.markCompleted("x.mp3", 1, T0)).isFalse();
        assertThat(job.updateProgress(new DownloadProgress(10, 1, 10, null, null, 0), T0)).isFalse();
        assertThat(job.getStatus()).isEqualTo(DownloadStatus.QUEUED);
    }

    @Test
    void terminalStatesAreImmutable() {
        DownloadJob job = newJob();
        assertThat(job.markCancelled(T0)).isTrue();

        assertThat(job.markAnalyzing(T0)).isFalse();
        assertThat(job.markFailed("X", "msg", T0)).isFalse();
        assertThat(job.markCancelled(T0)).isFalse();
        assertThat(job.isTerminal()).isTrue();
        assertThat(job.snapshot().status()).isEqualTo(DownloadStatus.CANCELLED);
    }

    @Test
    void failureStoresFriendlyMessage() {
        DownloadJob job = newJob();
        job.markAnalyzing(T0);

        assertThat(job.markFailed("SOURCE_FORBIDDEN", "Mensagem amigável", T0.plusSeconds(1))).isTrue();
        assertThat(job.snapshot().errorCode()).isEqualTo("SOURCE_FORBIDDEN");
        assertThat(job.snapshot().errorMessage()).isEqualTo("Mensagem amigável");
    }

    @Test
    void fileExpirationOnlyAppliesToCompletedJobs() {
        DownloadJob job = newJob();
        assertThat(job.markFileExpired(T0)).isFalse();

        job.markAnalyzing(T0);
        job.markDownloading(T0);
        job.markCompleted("a.mp3", 10, T0);
        assertThat(job.markFileExpired(T0.plusSeconds(60))).isTrue();
        assertThat(job.snapshot().fileAvailable()).isFalse();
        assertThat(job.markFileExpired(T0.plusSeconds(61))).isFalse();
    }
}
