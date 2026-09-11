package com.jloads.exception;

/** Requisição recusada por limites de uso (fila cheia, limites por cliente, servidor ocupado, etc.). */
public class RequestRejectedException extends ApplicationException {

    public RequestRejectedException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public RequestRejectedException(ErrorCode errorCode, String userMessage, String detail) {
        super(errorCode, userMessage, detail, null);
    }
}
