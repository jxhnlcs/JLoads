package com.jloads.dto;

import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.enums.DownloadStatus;
import java.util.UUID;

public record CreateDownloadResponse(UUID jobId, DownloadStatus status, DownloadResponse job) {

    public static CreateDownloadResponse from(DownloadJobSnapshot snapshot) {
        return new CreateDownloadResponse(snapshot.id(), snapshot.status(), DownloadResponse.from(snapshot));
    }
}
