package com.jloads.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jloads.model.DownloadProgress;
import org.junit.jupiter.api.Test;

class YtDlpProgressParserTest {

    private final YtDlpProgressParser parser = new YtDlpProgressParser();

    @Test
    void parsesProgressLineWithAllFields() {
        var event = parser.parse("YTDLP_PROGRESS|downloading|12345678|21500000|NA|4404019.2|8");

        assertThat(event).containsInstanceOf(YtDlpOutputEvent.Progress.class);
        var progress = (YtDlpOutputEvent.Progress) event.orElseThrow();
        assertThat(progress.status()).isEqualTo("downloading");
        assertThat(progress.downloadedBytes()).isEqualTo(12_345_678L);
        assertThat(progress.totalBytes()).isEqualTo(21_500_000L);
        assertThat(progress.totalBytesEstimate()).isNull();
        assertThat(progress.speedBytesPerSecond()).isEqualTo(4_404_019.2);
        assertThat(progress.etaSeconds()).isEqualTo(8L);
    }

    @Test
    void toleratesMissingValuesAndEstimates() {
        var progress = (YtDlpOutputEvent.Progress) parser
                .parse("YTDLP_PROGRESS|downloading|1024|NA|50000.7|NA|NA").orElseThrow();

        assertThat(progress.totalBytes()).isNull();
        assertThat(progress.bestTotal()).isEqualTo(50_001L);
        assertThat(progress.speedBytesPerSecond()).isNull();
    }

    @Test
    void parsesPostProcessLines() {
        var event = (YtDlpOutputEvent.PostProcess) parser.parse("YTDLP_POSTPROCESS|started|ExtractAudio").orElseThrow();
        assertThat(event.started()).isTrue();
        assertThat(event.isMediaProcessing()).isTrue();

        var move = (YtDlpOutputEvent.PostProcess) parser.parse("YTDLP_POSTPROCESS|started|MoveFiles").orElseThrow();
        assertThat(move.isMediaProcessing()).isFalse();
    }

    @Test
    void ignoresRegularLogAndMalformedLines() {
        assertThat(parser.parse("[download] Destination: media.f140.m4a")).isEmpty();
        assertThat(parser.parse("[download]  57.0% of 10.00MiB at 4.20MiB/s ETA 00:08")).isEmpty();
        assertThat(parser.parse("YTDLP_PROGRESS|downloading|1|2")).isEmpty();
        assertThat(parser.parse("YTDLP_PROGRESS|downloading|abc|-5|NA|NaN|x"))
                .get().extracting(e -> ((YtDlpOutputEvent.Progress) e).downloadedBytes()).isNull();
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void accumulatorProducesFormattedProgress() {
        var accumulator = parser.newAccumulator();
        DownloadProgress progress = accumulator.accept(progress("downloading", 12_345_678L, 21_500_000L, 4_404_019.2, 8L));

        assertThat(progress.percentage()).isEqualTo(57);
        assertThat(progress.downloadedBytes()).isEqualTo(12_345_678L);
        assertThat(progress.totalBytes()).isEqualTo(21_500_000L);
        assertThat(progress.speed()).isEqualTo("4.2 MB/s");
        assertThat(progress.eta()).isEqualTo("00:08");
    }

    @Test
    void accumulatorDoesNotResetBetweenStreams() {
        var accumulator = parser.newAccumulator();
        accumulator.accept(progress("downloading", 900L, 1000L, null, null));
        DownloadProgress afterVideo = accumulator.accept(progress("finished", 1000L, 1000L, null, null));
        DownloadProgress audioStart = accumulator.accept(progress("downloading", 10L, 100L, null, null));

        assertThat(afterVideo.percentage()).isEqualTo(99);
        assertThat(audioStart.downloadedBytes()).isEqualTo(1010L);
        assertThat(audioStart.totalBytes()).isEqualTo(1100L);
        assertThat(audioStart.percentage()).isEqualTo(91);
    }

    @Test
    void accumulatorNeverReportsHundredWhileDownloading() {
        var accumulator = parser.newAccumulator();
        assertThat(accumulator.accept(progress("downloading", 1000L, 1000L, null, null)).percentage()).isEqualTo(99);
        assertThat(accumulator.accept(progress("downloading", 500L, null, null, null)).percentage()).isZero();
    }

    private static YtDlpOutputEvent.Progress progress(String status, Long downloaded, Long total, Double speed, Long eta) {
        return new YtDlpOutputEvent.Progress(status, downloaded, total, null, speed, eta);
    }
}
