package com.jloads.service;

import com.jloads.config.AppProperties;
import com.jloads.model.VideoUrl;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;
import com.jloads.process.ExternalBinaries;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Monta as linhas de comando do yt-dlp a partir de uma allowlist interna de opções. Nenhum valor vindo do
 * usuário entra no comando além do ID de vídeo já validado (via {@link VideoUrl#canonicalUrl()}), sempre
 * posicionado após "--" para jamais ser interpretado como opção.
 */
@Component
@RequiredArgsConstructor
public class YtDlpCommandBuilder {

    public static final String PROGRESS_PREFIX = "YTDLP_PROGRESS|";
    public static final String POSTPROCESS_PREFIX = "YTDLP_POSTPROCESS|";
    public static final String OUTPUT_TEMPLATE = "media.%(ext)s";

    static final String DOWNLOAD_PROGRESS_TEMPLATE = "download:" + PROGRESS_PREFIX
            + "%(progress.status)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s"
            + "|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s";
    static final String POSTPROCESS_PROGRESS_TEMPLATE = "postprocess:" + POSTPROCESS_PREFIX
            + "%(progress.status)s|%(progress.postprocessor)s";

    private final AppProperties properties;
    private final ExternalBinaries binaries;

    public List<String> metadataCommand(VideoUrl url) {
        Objects.requireNonNull(url, "url");
        List<String> command = baseCommand();
        command.add("--no-warnings");
        command.add("--dump-single-json");
        command.add("--skip-download");
        appendUrl(command, url);
        return List.copyOf(command);
    }

    public List<String> downloadCommand(VideoUrl url, DownloadType type, DownloadQuality quality, Path outputDirectory) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(outputDirectory, "outputDirectory");

        AppProperties.Downloads downloads = properties.downloads();
        List<String> command = baseCommand();
        command.add("--newline");
        command.add("--no-mtime");
        command.add("--progress-template");
        command.add(DOWNLOAD_PROGRESS_TEMPLATE);
        command.add("--progress-template");
        command.add(POSTPROCESS_PROGRESS_TEMPLATE);
        command.add("--fragment-retries");
        command.add(String.valueOf(properties.ytdlp().retries()));
        command.add("--max-filesize");
        command.add(String.valueOf(downloads.maxFileSize().toBytes()));
        if (downloads.limitRate() != null && !downloads.limitRate().isBlank()) {
            command.add("--limit-rate");
            command.add(downloads.limitRate());
        }
        binaries.ffmpegLocation().ifPresent(path -> {
            command.add("--ffmpeg-location");
            command.add(path.toString());
        });
        command.add("--paths");
        command.add(outputDirectory.toAbsolutePath().normalize().toString());
        command.add("--output");
        command.add(OUTPUT_TEMPLATE);

        switch (type) {
            case AUDIO -> appendAudioOptions(command, quality);
            case VIDEO -> appendVideoOptions(command, quality);
        }
        appendUrl(command, url);
        return List.copyOf(command);
    }

    private List<String> baseCommand() {
        AppProperties.YtDlp ytdlp = properties.ytdlp();
        List<String> command = new ArrayList<>();
        command.add(binaries.ytDlpCommand());
        command.add("--ignore-config");
        command.add("--encoding");
        command.add("utf-8");
        command.add("--no-playlist");
        command.add("--color");
        command.add("never");
        command.add("--socket-timeout");
        command.add(String.valueOf(ytdlp.socketTimeoutSeconds()));
        command.add("--retries");
        command.add(String.valueOf(ytdlp.retries()));
        binaries.jsRuntimeArgument().ifPresent(runtime -> {
            command.add("--no-js-runtimes");
            command.add("--js-runtimes");
            command.add(runtime);
        });
        return command;
    }

    private static void appendAudioOptions(List<String> command, DownloadQuality quality) {
        command.add("--format");
        command.add("bestaudio/best");
        command.add("--extract-audio");
        command.add("--audio-format");
        command.add("mp3");
        command.add("--audio-quality");
        command.add(quality.audioBitrateKbps() == null ? "0" : quality.audioBitrateKbps() + "K");
    }

    private static void appendVideoOptions(List<String> command, DownloadQuality quality) {
        String resolution = quality.maxVideoHeight() == null ? "res" : "res:" + quality.maxVideoHeight();
        command.add("--format");
        command.add("bv*+ba/b");
        command.add("--format-sort");
        command.add(resolution + ",ext:mp4:m4a");
        command.add("--merge-output-format");
        command.add("mp4");
    }

    private static void appendUrl(List<String> command, VideoUrl url) {
        command.add("--");
        command.add(url.canonicalUrl());
    }
}
