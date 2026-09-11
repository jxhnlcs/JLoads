package com.jloads.service;

import com.jloads.config.AppProperties;
import com.jloads.exception.DownloadCancelledException;
import com.jloads.exception.DownloadException;
import com.jloads.exception.DownloadTimeoutException;
import com.jloads.exception.ErrorCode;
import com.jloads.exception.VideoAnalysisException;
import com.jloads.model.DownloadProgress;
import com.jloads.model.VideoMetadata;
import com.jloads.model.VideoUrl;
import com.jloads.parser.YtDlpErrorTranslator;
import com.jloads.parser.YtDlpMetadataParser;
import com.jloads.parser.YtDlpOutputEvent;
import com.jloads.parser.YtDlpProgressParser;
import com.jloads.process.ProcessLauncher;
import com.jloads.process.ProcessRegistry;
import com.jloads.process.RunningProcess;
import com.jloads.process.RunningProcess.TerminationReason;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Integração com o yt-dlp como processo externo. É o único ponto da aplicação que executa o binário.
 */
@Slf4j
@Service
public class YtDlpService {

    private static final int MAX_METADATA_BYTES = 32 * 1024 * 1024;
    private static final int MAX_LINE_LENGTH = 4096;
    private static final int MAX_ERROR_LINES = 20;
    private static final Duration EXIT_GRACE = Duration.ofSeconds(30);

    private final YtDlpCommandBuilder commandBuilder;
    private final ProcessLauncher launcher;
    private final ProcessRegistry registry;
    private final YtDlpProgressParser progressParser;
    private final YtDlpMetadataParser metadataParser;
    private final YtDlpErrorTranslator errorTranslator;
    private final AppProperties properties;

    private final ExecutorService streamReaders = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("ytdlp-stream-", 0).factory());
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("ytdlp-watchdog").daemon(true).factory());

    public YtDlpService(YtDlpCommandBuilder commandBuilder, ProcessLauncher launcher, ProcessRegistry registry,
                        YtDlpProgressParser progressParser, YtDlpMetadataParser metadataParser,
                        YtDlpErrorTranslator errorTranslator, AppProperties properties) {
        this.commandBuilder = commandBuilder;
        this.launcher = launcher;
        this.registry = registry;
        this.progressParser = progressParser;
        this.metadataParser = metadataParser;
        this.errorTranslator = errorTranslator;
        this.properties = properties;
    }

    public VideoMetadata fetchMetadata(VideoUrl url) {
        Process process;
        try {
            process = launcher.start(commandBuilder.metadataCommand(url), null, false);
        } catch (IOException ex) {
            throw new VideoAnalysisException(ErrorCode.ENGINE_UNAVAILABLE, "failed to start yt-dlp", ex);
        }

        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(
                () -> readLimited(process.getInputStream(), MAX_METADATA_BYTES), streamReaders);
        CompletableFuture<List<String>> stderr = CompletableFuture.supplyAsync(
                () -> readErrorLines(process.getErrorStream()), streamReaders);
        try {
            Duration timeout = properties.ytdlp().metadataTimeout();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                RunningProcess.killTree(process);
                throw DownloadTimeoutException.analysis("metadata extraction exceeded " + timeout);
            }
            byte[] output = stdout.get(EXIT_GRACE.toSeconds(), TimeUnit.SECONDS);
            List<String> errors = stderr.get(EXIT_GRACE.toSeconds(), TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            if (exitCode != 0 || output == null || output.length == 0) {
                ErrorCode code = errorTranslator.translate(errors, ErrorCode.ANALYSIS_FAILED);
                log.info("Metadata extraction failed: code={} exit={} reason='{}'", code, exitCode, lastLine(errors));
                throw new VideoAnalysisException(code, "yt-dlp metadata exited with " + exitCode);
            }
            return metadataParser.parse(new String(output, StandardCharsets.UTF_8), url);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new VideoAnalysisException(ErrorCode.ANALYSIS_FAILED, "interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new VideoAnalysisException(ErrorCode.ANALYSIS_FAILED, "failed to read yt-dlp output", ex);
        } finally {
            if (process.isAlive()) {
                RunningProcess.killTree(process);
            }
        }
    }

    /**
     * Executa o download de forma bloqueante na thread chamadora (um worker da fila).
     *
     * @throws DownloadCancelledException se o job for cancelado
     * @throws DownloadTimeoutException   se exceder o tempo máximo
     * @throws DownloadException          em qualquer outra falha
     */
    public void download(DownloadCommand request, DownloadListener listener) {
        List<String> command = commandBuilder.downloadCommand(
                request.url(), request.type(), request.quality(), request.workspace());
        Process process;
        try {
            process = launcher.start(command, request.workspace(), true);
        } catch (IOException ex) {
            throw new DownloadException(ErrorCode.ENGINE_UNAVAILABLE, "failed to start yt-dlp", ex);
        }

        RunningProcess running = registry.register(request.jobId(), process);
        Duration timeout = properties.downloads().timeout();
        ScheduledFuture<?> timeoutTask = watchdog.schedule(
                () -> running.terminate(TerminationReason.TIMEOUT), timeout.toMillis(), TimeUnit.MILLISECONDS);
        OutputState state = new OutputState();
        try {
            if (listener.isCancelled()) {
                running.terminate(TerminationReason.CANCELLED);
            }
            consumeOutput(process, running, listener, state);
            if (!process.waitFor(EXIT_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                RunningProcess.killTree(process);
            }
            evaluateOutcome(running, process, listener, state);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running.terminate(TerminationReason.SHUTDOWN);
            throw new DownloadException(ErrorCode.JOB_INTERRUPTED, "worker interrupted", ex);
        } finally {
            timeoutTask.cancel(false);
            registry.unregister(request.jobId(), running);
            if (process.isAlive()) {
                RunningProcess.killTree(process);
            }
        }
    }

    private void consumeOutput(Process process, RunningProcess running, DownloadListener listener, OutputState state) {
        long maxBytes = properties.downloads().maxFileSize().toBytes();
        YtDlpProgressParser.ProgressAccumulator accumulator = progressParser.newAccumulator();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String safeLine = line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line;
                var event = progressParser.parse(safeLine);
                if (event.isPresent()) {
                    switch (event.get()) {
                        case YtDlpOutputEvent.Progress progress -> {
                            DownloadProgress aggregated = accumulator.accept(progress);
                            if (aggregated.downloadedBytes() > maxBytes || progress.bestTotal() > maxBytes) {
                                running.terminate(TerminationReason.SIZE_LIMIT);
                            } else {
                                listener.onProgress(aggregated);
                            }
                        }
                        case YtDlpOutputEvent.PostProcess postProcess -> {
                            if (postProcess.started() && postProcess.isMediaProcessing()) {
                                listener.onProcessingStarted(postProcess.postprocessor());
                            }
                        }
                    }
                } else if (safeLine.startsWith("ERROR:")) {
                    if (state.errorLines.size() < MAX_ERROR_LINES) {
                        state.errorLines.add(safeLine);
                    }
                } else if (safeLine.contains("larger than max-filesize")) {
                    state.sizeLimitReached = true;
                }
                if (listener.isCancelled() && running.terminationReason() == null) {
                    running.terminate(TerminationReason.CANCELLED);
                }
            }
        } catch (IOException ex) {
            // Esperado quando o processo é encerrado à força; o resultado é avaliado pelo motivo registrado.
            log.debug("yt-dlp output stream closed: {}", ex.getMessage());
        }
    }

    private void evaluateOutcome(RunningProcess running, Process process, DownloadListener listener, OutputState state) {
        TerminationReason reason = running.terminationReason();
        if (reason == TerminationReason.CANCELLED || listener.isCancelled()) {
            throw new DownloadCancelledException("download cancelled");
        }
        if (reason == TerminationReason.SHUTDOWN) {
            throw new DownloadException(ErrorCode.JOB_INTERRUPTED, "terminated on shutdown");
        }
        if (reason == TerminationReason.TIMEOUT) {
            throw DownloadTimeoutException.download("download exceeded " + properties.downloads().timeout());
        }
        if (reason == TerminationReason.SIZE_LIMIT || state.sizeLimitReached) {
            throw new DownloadException(ErrorCode.FILE_TOO_LARGE, "max file size reached");
        }
        int exitCode = process.isAlive() ? -1 : process.exitValue();
        if (exitCode != 0) {
            ErrorCode code = errorTranslator.translate(state.errorLines, ErrorCode.DOWNLOAD_FAILED);
            log.warn("yt-dlp download failed: code={} exit={} reason='{}'", code, exitCode, lastLine(state.errorLines));
            throw new DownloadException(code, "yt-dlp exited with " + exitCode);
        }
    }

    private static byte[] readLimited(InputStream stream, int maxBytes) {
        try (stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            boolean overflow = false;
            while ((read = stream.read(buffer)) != -1) {
                if (!overflow && out.size() + read <= maxBytes) {
                    out.write(buffer, 0, read);
                } else {
                    overflow = true; // continua drenando para o processo não bloquear
                }
            }
            return overflow ? new byte[0] : out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<String> readErrorLines(InputStream stream) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ERROR:") && lines.size() < MAX_ERROR_LINES) {
                    lines.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line);
                }
            }
        } catch (IOException ex) {
            log.debug("yt-dlp error stream closed: {}", ex.getMessage());
        }
        return lines;
    }

    private static String lastLine(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        String last = lines.getLast();
        return last.length() > 300 ? last.substring(0, 300) : last;
    }

    @PreDestroy
    void shutdown() {
        watchdog.shutdownNow();
        streamReaders.shutdownNow();
    }

    private static final class OutputState {
        private final List<String> errorLines = new ArrayList<>();
        private boolean sizeLimitReached;
    }
}
