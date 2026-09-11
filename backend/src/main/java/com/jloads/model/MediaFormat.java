package com.jloads.model;

/** Formato de mídia disponível, reduzido aos campos relevantes para decidir as opções oferecidas. */
public record MediaFormat(
        String formatId,
        String extension,
        boolean hasVideo,
        boolean hasAudio,
        Integer height,
        Long sizeBytes,
        boolean drmProtected) {
}
