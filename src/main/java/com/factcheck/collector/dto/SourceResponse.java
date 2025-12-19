package com.factcheck.collector.dto;

import com.factcheck.collector.domain.enums.SourceType;

import java.time.Instant;

public record SourceResponse(
        Long id,
        String name,
        SourceType type,
        String url,
        String category,
        boolean enabled,
        double reliabilityScore,
        Instant lastFetchedAt,
        Instant lastSuccessAt,
        int failureCount,
        Instant createdAt,
        Instant updatedAt
) {
}
