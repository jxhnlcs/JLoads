package com.jloads.parser;

import com.jloads.model.DownloadProgress;
import com.jloads.service.YtDlpCommandBuilder;
import com.jloads.util.HumanFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Interpreta a saída estruturada gerada pelos templates {@code --progress-template} definidos em
 * {@link YtDlpCommandBuilder}. Linhas comuns de log do yt-dlp são ignoradas.
 */
@Component
public class YtDlpProgressParser {

    private static final Pattern SEPARATOR = Pattern.compile("\\|");

    public Optional<YtDlpOutputEvent> parse(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String trimmed = line.strip();
        if (trimmed.startsWith(YtDlpCommandBuilder.PROGRESS_PREFIX)) {
            return parseProgress(trimmed.substring(YtDlpCommandBuilder.PROGRESS_PREFIX.length()));
        }
        if (trimmed.startsWith(YtDlpCommandBuilder.POSTPROCESS_PREFIX)) {
            return parsePostProcess(trimmed.substring(YtDlpCommandBuilder.POSTPROCESS_PREFIX.length()));
        }
        return Optional.empty();
    }

    /** Cria um acumulador para um download (que pode conter vários streams, ex.: vídeo + áudio). */
    public ProgressAccumulator newAccumulator() {
        return new ProgressAccumulator();
    }

    private static Optional<YtDlpOutputEvent> parseProgress(String payload) {
        String[] parts = SEPARATOR.split(payload, -1);
        if (parts.length != 6) {
            return Optional.empty();
        }
        return Optional.of(new YtDlpOutputEvent.Progress(
                text(parts[0]),
                wholeNumber(parts[1]),
                wholeNumber(parts[2]),
                wholeNumber(parts[3]),
                decimal(parts[4]),
                wholeNumber(parts[5])));
    }

    private static Optional<YtDlpOutputEvent> parsePostProcess(String payload) {
        String[] parts = SEPARATOR.split(payload, -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        return Optional.of(new YtDlpOutputEvent.PostProcess(text(parts[0]), text(parts[1])));
    }

    private static String text(String raw) {
        String value = raw.strip();
        return value.isEmpty() || isPlaceholder(value) ? null : value;
    }

    private static Double decimal(String raw) {
        String value = raw.strip();
        if (value.isEmpty() || isPlaceholder(value)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long wholeNumber(String raw) {
        Double value = decimal(raw);
        return value == null ? null : Math.round(value);
    }

    private static boolean isPlaceholder(String value) {
        return value.equals("NA") || value.equals("None") || value.equals("null");
    }

    /**
     * Agrega o progresso de múltiplos streams consecutivos de um mesmo download, evitando que a barra
     * volte a 0% quando o yt-dlp passa do stream de vídeo para o de áudio.
     */
    public static final class ProgressAccumulator {

        private long completedStreamsBytes;

        public DownloadProgress accept(YtDlpOutputEvent.Progress event) {
            long current = event.downloadedBytes() == null ? 0 : event.downloadedBytes();
            long reportedTotal = event.bestTotal();
            long currentTotal = reportedTotal > 0 ? Math.max(reportedTotal, current) : 0;

            if (event.finished()) {
                completedStreamsBytes += Math.max(currentTotal, current);
                return new DownloadProgress(99, completedStreamsBytes, completedStreamsBytes, null, null, 0);
            }

            long downloaded = completedStreamsBytes + current;
            long total = currentTotal > 0 ? completedStreamsBytes + currentTotal : 0;
            int percentage = total > 0 ? (int) Math.min(99, (downloaded * 100) / total) : 0;
            return new DownloadProgress(
                    percentage,
                    downloaded,
                    total,
                    HumanFormat.speed(event.speedBytesPerSecond()),
                    HumanFormat.eta(event.etaSeconds()),
                    event.speedBytesPerSecond() == null ? 0 : Math.round(event.speedBytesPerSecond()));
        }
    }
}
