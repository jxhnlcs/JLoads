package com.jloads.exception;

import java.util.UUID;

public class JobNotFoundException extends ApplicationException {

    public JobNotFoundException(UUID jobId) {
        super(ErrorCode.JOB_NOT_FOUND, "job not found: " + jobId);
    }
}
