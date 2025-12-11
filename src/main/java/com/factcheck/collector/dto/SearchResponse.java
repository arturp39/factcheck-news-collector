package com.factcheck.collector.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchResponse {
    private List<ChunkResult> results;
    private Integer totalFound;
    private Long executionTimeMs;
    private String correlationId;
}
