package com.jloads.exception;

public class DownloadException extends ApplicationException {

    public DownloadException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public DownloadException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
