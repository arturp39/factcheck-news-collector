package com.factcheck.collector.dto;

import java.time.Instant;

public record IngestionRunResponse(
        Long id,
        Long sourceId,
        String sourceName,
        Instant startedAt,
        Instant completedAt,
        int articlesFetched,
        int articlesProcessed,
        int articlesFailed,
        String status,
        String errorDetails,
        String correlationId
) {
}
