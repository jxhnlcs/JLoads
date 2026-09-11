package com.jloads.service;

import com.jloads.model.VideoUrl;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Parâmetros internos de um download a ser executado pelo yt-dlp. */
public record DownloadCommand(UUID jobId, VideoUrl url, DownloadType type, DownloadQuality quality, Path workspace) {

    public DownloadCommand {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(workspace, "workspace");
    }
}
