package com.jloads.dto;

import com.jloads.model.FormatOption;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;

public record FormatOptionResponse(
        DownloadType type,
        DownloadQuality quality,
        String label,
        String description,
        Long estimatedSizeBytes) {

    public static FormatOptionResponse from(FormatOption option) {
        return new FormatOptionResponse(
                option.type(), option.quality(), option.label(), option.description(), option.estimatedSizeBytes());
    }
}
