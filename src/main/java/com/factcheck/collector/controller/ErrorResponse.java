package com.factcheck.collector.controller;

import java.time.OffsetDateTime;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {
    public static ErrorResponse of(int status,
                                   String error,
                                   String message,
                                   String path,
                                   String correlationId) {
        return new ErrorResponse(
                OffsetDateTime.now(),
                status,
                error,
                message,
                path,
                correlationId
        );
    }
}