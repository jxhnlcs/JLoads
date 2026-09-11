package com.jloads.exception;

public class VideoAnalysisException extends ApplicationException {

    public VideoAnalysisException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public VideoAnalysisException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
