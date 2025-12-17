package com.factcheck.collector.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchRequest {

    @NotNull
    private List<Double> embedding;

    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer limit = 10;

    @Min(0)
    @Max(1)
    @Builder.Default
    private Float minScore = 0.7f;

    private SearchFilters filters;
}