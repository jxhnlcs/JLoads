package com.jloads.exception;

public class DownloadCancelledException extends ApplicationException {

    public DownloadCancelledException(String detail) {
        super(ErrorCode.DOWNLOAD_CANCELLED, detail);
    }
}
