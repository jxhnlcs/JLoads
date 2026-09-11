package com.jloads.model;

import java.util.List;

public record VideoMetadata(
        String videoId,
        String title,
        String channel,
        Long durationSeconds,
        String thumbnailUrl,
        boolean live,
        List<MediaFormat> formats) {

    public VideoMetadata {
        formats = formats == null ? List.of() : List.copyOf(formats);
    }
}
