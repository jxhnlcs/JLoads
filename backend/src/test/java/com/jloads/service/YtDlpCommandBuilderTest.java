package com.jloads.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jloads.config.AppProperties;
import com.jloads.model.VideoUrl;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;
import com.jloads.process.ExternalBinaries;
import com.jloads.support.TestProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class YtDlpCommandBuilderTest {

    private final AppProperties properties = TestProperties.defaults();
    private final YtDlpCommandBuilder builder = new YtDlpCommandBuilder(properties, new ExternalBinaries(properties));
    private final VideoUrl url = new VideoUrl("jNQXAC9IVRw");

    @Test
    void metadataCommandUsesSafeFixedOptions() {
        List<String> command = builder.metadataCommand(url);

        assertThat(command).containsSubsequence("--ignore-config", "--no-playlist");
        assertThat(command).containsSequence("--dump-single-json", "--skip-download");
        assertThat(command).containsSequence("--no-js-runtimes", "--js-runtimes", "node");
        assertThat(command).endsWith("--", "https://www.youtube.com/watch?v=jNQXAC9IVRw");
        assertThat(command).doesNotContain("--exec", "--cookies", "--cookies-from-browser", "--config-locations");
    }

    @Test
    void audioCommandExtractsMp3WithRequestedBitrate() {
        List<String> command = builder.downloadCommand(url, DownloadType.AUDIO, DownloadQuality.MEDIUM, Path.of("ws"));

        assertThat(command).containsSequence("--format", "bestaudio/best");
        assertThat(command).containsSequence("--extract-audio", "--audio-format", "mp3", "--audio-quality", "192K");
        assertThat(command).containsSequence("--output", "media.%(ext)s");
        assertThat(command).containsSequence("--max-filesize", String.valueOf(100L * 1024 * 1024));
        assertThat(command).contains("--newline");
        assertThat(command.get(command.size() - 2)).isEqualTo("--");
    }

    @Test
    void bestAudioUsesVbrZero() {
        List<String> command = builder.downloadCommand(url, DownloadType.AUDIO, DownloadQuality.BEST, Path.of("ws"));
        assertThat(command).containsSequence("--audio-quality", "0");
    }

    @Test
    void videoCommandLimitsResolutionAndMergesToMp4() {
        List<String> high = builder.downloadCommand(url, DownloadType.VIDEO, DownloadQuality.HIGH, Path.of("ws"));
        List<String> best = builder.downloadCommand(url, DownloadType.VIDEO, DownloadQuality.BEST, Path.of("ws"));

        assertThat(high).containsSequence("--format-sort", "res:1080,ext:mp4:m4a");
        assertThat(high).containsSequence("--merge-output-format", "mp4");
        assertThat(best).containsSequence("--format-sort", "res,ext:mp4:m4a");
        assertThat(high).doesNotContain("--extract-audio");
    }

    @Test
    void progressTemplatesAreStructured() {
        List<String> command = builder.downloadCommand(url, DownloadType.VIDEO, DownloadQuality.LOW, Path.of("ws"));

        assertThat(command).contains(YtDlpCommandBuilder.DOWNLOAD_PROGRESS_TEMPLATE, YtDlpCommandBuilder.POSTPROCESS_PROGRESS_TEMPLATE);
        assertThat(YtDlpCommandBuilder.DOWNLOAD_PROGRESS_TEMPLATE).startsWith("download:YTDLP_PROGRESS|");
    }

    @Test
    void outputDirectoryIsAbsolute() {
        List<String> command = builder.downloadCommand(url, DownloadType.AUDIO, DownloadQuality.LOW, Path.of("storage", "temporary", "x"));
        String path = command.get(command.indexOf("--paths") + 1);
        assertThat(Path.of(path)).isAbsolute();
    }
}
