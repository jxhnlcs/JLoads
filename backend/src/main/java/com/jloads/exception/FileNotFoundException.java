package com.jloads.exception;

/** Arquivo de um job indisponível (ainda não concluído, expirado ou removido). */
public class FileNotFoundException extends ApplicationException {

    public FileNotFoundException(String detail) {
        super(ErrorCode.FILE_NOT_AVAILABLE, detail);
    }

    public FileNotFoundException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public static FileNotFoundException expired(String detail) {
        return new FileNotFoundException(ErrorCode.FILE_EXPIRED, detail);
    }
}
