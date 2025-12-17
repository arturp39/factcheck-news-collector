package com.factcheck.collector.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(ex, request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> build(Exception ex,
                                                HttpServletRequest request,
                                                HttpStatus status) {
        String correlationId = MDC.get("corrId");
        String path = request.getRequestURI();
        log.error("Request failed status={} path={} corrId={}", status.value(), path, correlationId, ex);

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                path,
                correlationId
        );
        return ResponseEntity.status(status).body(body);
    }
}