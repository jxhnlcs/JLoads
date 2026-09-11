package com.jloads.service;

import com.jloads.model.FormatOption;
import com.jloads.model.MediaFormat;
import com.jloads.model.VideoMetadata;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Decide quais combinações de tipo/qualidade fazem sentido para um conteúdo específico. */
@Component
public class FormatOptionsResolver {

    private static final int BEST_VBR_ESTIMATED_KBPS = 245;

    public List<FormatOption> resolve(VideoMetadata metadata) {
        List<MediaFormat> usable = metadata.formats().stream().filter(f -> !f.drmProtected()).toList();
        List<FormatOption> options = new ArrayList<>();
        options.addAll(videoOptions(usable));
        options.addAll(audioOptions(usable, metadata.durationSeconds()));
        return List.copyOf(options);
    }

    /**
     * Qualidades são limites superiores: pedir "até 1080p" num vídeo de 720p baixa o melhor disponível (720p).
     * Por isso basta existir algum formato do tipo solicitado — essencial para lotes com um preset único.
     */
    public boolean supportsType(VideoMetadata metadata, DownloadType type) {
        return resolve(metadata).stream().anyMatch(o -> o.type() == type);
    }

    private static List<FormatOption> videoOptions(List<MediaFormat> formats) {
        List<MediaFormat> videos = formats.stream().filter(f -> f.hasVideo() && f.height() != null).toList();
        if (videos.isEmpty()) {
            return List.of();
        }
        int maxHeight = videos.stream().mapToInt(MediaFormat::height).max().orElseThrow();
        Long bestAudioSize = bestAudio(formats).map(MediaFormat::sizeBytes).orElse(null);

        List<FormatOption> options = new ArrayList<>();
        options.add(new FormatOption(DownloadType.VIDEO, DownloadQuality.BEST, "Melhor disponível",
                "MP4 · " + maxHeight + "p", estimateVideo(videos, maxHeight, bestAudioSize)));
        for (DownloadQuality quality : List.of(DownloadQuality.HIGH, DownloadQuality.MEDIUM, DownloadQuality.LOW)) {
            int cap = quality.maxVideoHeight();
            if (maxHeight > cap) {
                options.add(new FormatOption(DownloadType.VIDEO, quality, videoLabel(cap),
                        "MP4 · até " + cap + "p", estimateVideo(videos, cap, bestAudioSize)));
            }
        }
        return options;
    }

    private static List<FormatOption> audioOptions(List<MediaFormat> formats, Long durationSeconds) {
        if (formats.stream().noneMatch(MediaFormat::hasAudio)) {
            return List.of();
        }
        return List.of(
                new FormatOption(DownloadType.AUDIO, DownloadQuality.BEST, "Máxima qualidade",
                        "MP3 · VBR ~" + BEST_VBR_ESTIMATED_KBPS + " kbps",
                        estimateAudio(durationSeconds, BEST_VBR_ESTIMATED_KBPS)),
                audioOption(DownloadQuality.HIGH, "Alta", durationSeconds),
                audioOption(DownloadQuality.MEDIUM, "Média", durationSeconds),
                audioOption(DownloadQuality.LOW, "Econômica", durationSeconds));
    }

    private static FormatOption audioOption(DownloadQuality quality, String label, Long durationSeconds) {
        int kbps = quality.audioBitrateKbps();
        return new FormatOption(DownloadType.AUDIO, quality, label, "MP3 · " + kbps + " kbps",
                estimateAudio(durationSeconds, kbps));
    }

    private static String videoLabel(int height) {
        return switch (height) {
            case 1080 -> "Full HD";
            case 720 -> "HD";
            default -> "SD";
        };
    }

    private static Long estimateVideo(List<MediaFormat> videos, int cap, Long audioSize) {
        Optional<Integer> chosenHeight = videos.stream()
                .map(MediaFormat::height)
                .filter(h -> h <= cap)
                .max(Integer::compare);
        if (chosenHeight.isEmpty()) {
            return null;
        }
        Optional<MediaFormat> chosen = videos.stream()
                .filter(f -> f.height().equals(chosenHeight.get()) && f.sizeBytes() != null)
                .max(Comparator.comparing((MediaFormat f) -> "mp4".equals(f.extension()))
                        .thenComparing(MediaFormat::sizeBytes));
        if (chosen.isEmpty()) {
            return null;
        }
        boolean muxed = chosen.get().hasAudio();
        if (muxed) {
            return chosen.get().sizeBytes();
        }
        return audioSize == null ? null : chosen.get().sizeBytes() + audioSize;
    }

    private static Optional<MediaFormat> bestAudio(List<MediaFormat> formats) {
        return formats.stream()
                .filter(f -> f.hasAudio() && !f.hasVideo() && f.sizeBytes() != null)
                .max(Comparator.comparing((MediaFormat f) -> "m4a".equals(f.extension()))
                        .thenComparing(MediaFormat::sizeBytes));
    }

    private static Long estimateAudio(Long durationSeconds, int kbps) {
        return durationSeconds == null ? null : durationSeconds * kbps * 1000 / 8;
    }
}
