package com.jloads.model.enums;

public enum DownloadEventType {
    JOB_QUEUED,
    JOB_STARTED,
    JOB_PROGRESS,
    JOB_PROCESSING,
    JOB_COMPLETED,
    JOB_FAILED,
    JOB_CANCELLED,
    /** O arquivo de um job concluído foi removido pela limpeza automática. */
    JOB_EXPIRED
}
