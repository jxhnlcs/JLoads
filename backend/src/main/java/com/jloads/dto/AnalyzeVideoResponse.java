package com.jloads.dto;

import com.jloads.service.VideoAnalysis;
import java.util.List;

public record AnalyzeVideoResponse(
        String videoId,
        String url,
        String title,
        String thumbnail,
        String channel,
        Long duration,
        List<FormatOptionResponse> availableOptions) {

    public static AnalyzeVideoResponse from(VideoAnalysis analysis) {
        return new AnalyzeVideoResponse(
                analysis.url().videoId(),
                analysis.url().canonicalUrl(),
                analysis.metadata().title(),
                analysis.metadata().thumbnailUrl(),
                analysis.metadata().channel(),
                analysis.metadata().durationSeconds(),
                analysis.options().stream().map(FormatOptionResponse::from).toList());
    }
}
