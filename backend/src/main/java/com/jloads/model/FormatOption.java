package com.jloads.model;

import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;

/** Combinação tipo/qualidade oferecida ao usuário para um conteúdo específico. */
public record FormatOption(
        DownloadType type,
        DownloadQuality quality,
        String label,
        String description,
        Long estimatedSizeBytes) {
}
