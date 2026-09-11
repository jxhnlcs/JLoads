package com.jloads.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * URL de vídeo já validada. Apenas o ID extraído é mantido; a URL enviada ao yt-dlp é sempre
 * reconstruída a partir dele, nunca repassada a partir da entrada do usuário.
 */
public record VideoUrl(String videoId) {

    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    public VideoUrl {
        Objects.requireNonNull(videoId, "videoId");
        if (!isValidVideoId(videoId)) {
            throw new IllegalArgumentException("videoId inválido");
        }
    }

    public static boolean isValidVideoId(String candidate) {
        return candidate != null && VIDEO_ID.matcher(candidate).matches();
    }

    public String canonicalUrl() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    public String fallbackThumbnailUrl() {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }
}
