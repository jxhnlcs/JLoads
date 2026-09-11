package com.jloads.exception;

public class UnsupportedFormatException extends ApplicationException {

    public UnsupportedFormatException(String detail) {
        super(ErrorCode.UNSUPPORTED_FORMAT, detail);
    }
}
