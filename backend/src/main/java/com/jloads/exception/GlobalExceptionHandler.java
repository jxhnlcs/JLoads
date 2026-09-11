package com.jloads.exception;

import com.jloads.dto.ErrorResponse;
import com.jloads.web.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Converte exceções em respostas padronizadas, sem expor stack traces, comandos ou detalhes internos. */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ApplicationException ex) {
        ErrorCode code = ex.getErrorCode();
        if (code.httpStatus().is5xxServerError()) {
            log.warn("Request failed with {}: {}", code, ex.getMessage());
        } else {
            log.debug("Request rejected with {}: {}", code, ex.getMessage());
        }
        return build(code, ex.getUserMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_REQUEST.defaultMessage());
        return build(ErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        log.debug("Malformed request: {}", ex.getClass().getSimpleName());
        return build(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.defaultMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(clock), code.httpStatus().value(), code.name(), message,
                MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(code.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
