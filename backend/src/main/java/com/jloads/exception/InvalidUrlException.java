package com.jloads.exception;

public class InvalidUrlException extends ApplicationException {

    public InvalidUrlException(String detail) {
        super(ErrorCode.INVALID_URL, detail);
    }

    public InvalidUrlException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public InvalidUrlException(ErrorCode errorCode, String userMessage, String detail) {
        super(errorCode, userMessage, detail, null);
    }

    public static InvalidUrlException unsupported(String detail) {
        return new InvalidUrlException(ErrorCode.UNSUPPORTED_URL, detail);
    }
}
