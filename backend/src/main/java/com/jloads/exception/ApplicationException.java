package com.jloads.exception;

import java.util.Objects;

/**
 * Base das exceções de negócio. A mensagem da exceção ({@link #getMessage()}) é um detalhe técnico para
 * logs; ao usuário é exibida apenas {@link #getUserMessage()}.
 */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String userMessage;

    protected ApplicationException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    protected ApplicationException(ErrorCode errorCode, String detail, Throwable cause) {
        this(errorCode, errorCode.defaultMessage(), detail, cause);
    }

    /** Permite uma mensagem amigável específica (ex.: indicando qual link de um lote é inválido). */
    protected ApplicationException(ErrorCode errorCode, String userMessage, String detail, Throwable cause) {
        super(detail == null ? errorCode.name() : detail, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.userMessage = userMessage == null ? errorCode.defaultMessage() : userMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
