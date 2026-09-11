package com.jloads.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum DownloadStatus {
    QUEUED,
    ANALYZING,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return !isTerminal();
    }

    public boolean canTransitionTo(DownloadStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<DownloadStatus> allowedTargets() {
        return switch (this) {
            case QUEUED -> EnumSet.of(ANALYZING, FAILED, CANCELLED);
            case ANALYZING -> EnumSet.of(DOWNLOADING, FAILED, CANCELLED);
            case DOWNLOADING -> EnumSet.of(PROCESSING, COMPLETED, FAILED, CANCELLED);
            case PROCESSING -> EnumSet.of(COMPLETED, FAILED, CANCELLED);
            case COMPLETED, FAILED, CANCELLED -> EnumSet.noneOf(DownloadStatus.class);
        };
    }
}
