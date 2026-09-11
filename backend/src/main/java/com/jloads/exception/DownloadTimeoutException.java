package com.jloads.exception;

public class DownloadTimeoutException extends ApplicationException {

    public DownloadTimeoutException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public static DownloadTimeoutException download(String detail) {
        return new DownloadTimeoutException(ErrorCode.DOWNLOAD_TIMEOUT, detail);
    }

    public static DownloadTimeoutException analysis(String detail) {
        return new DownloadTimeoutException(ErrorCode.ANALYSIS_TIMEOUT, detail);
    }
}
