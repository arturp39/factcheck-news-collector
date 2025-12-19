package com.factcheck.collector.dto;

import java.util.List;

public record IngestionLogPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<IngestionRunResponse> items
) {
}
