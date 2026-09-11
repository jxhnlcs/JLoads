package com.jloads.dto;

import com.jloads.model.DownloadJobSnapshot;
import java.util.List;

public record CreateBatchDownloadResponse(List<DownloadResponse> jobs) {

    public static CreateBatchDownloadResponse from(List<DownloadJobSnapshot> snapshots) {
        return new CreateBatchDownloadResponse(snapshots.stream().map(DownloadResponse::from).toList());
    }
}
