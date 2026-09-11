package com.jloads.service;

import com.jloads.model.FormatOption;
import com.jloads.model.VideoMetadata;
import com.jloads.model.VideoUrl;
import java.util.List;

/** Resultado da análise de uma URL. */
public record VideoAnalysis(VideoUrl url, VideoMetadata metadata, List<FormatOption> options) {
}
