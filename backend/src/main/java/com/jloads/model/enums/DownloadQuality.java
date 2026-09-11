package com.jloads.model.enums;

/**
 * Nível de qualidade solicitado. O significado depende do {@link DownloadType}:
 * para vídeo limita a resolução; para áudio define o bitrate do MP3.
 */
public enum DownloadQuality {
    BEST(null, null),
    HIGH(1080, 256),
    MEDIUM(720, 192),
    LOW(480, 128);

    private final Integer maxVideoHeight;
    private final Integer audioBitrateKbps;

    DownloadQuality(Integer maxVideoHeight, Integer audioBitrateKbps) {
        this.maxVideoHeight = maxVideoHeight;
        this.audioBitrateKbps = audioBitrateKbps;
    }

    /** Altura máxima do vídeo; {@code null} significa sem limite. */
    public Integer maxVideoHeight() {
        return maxVideoHeight;
    }

    /** Bitrate constante do MP3; {@code null} significa VBR de maior qualidade. */
    public Integer audioBitrateKbps() {
        return audioBitrateKbps;
    }
}
