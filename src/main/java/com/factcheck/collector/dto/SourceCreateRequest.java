package com.factcheck.collector.dto;

import com.factcheck.collector.domain.enums.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SourceCreateRequest(
        @NotBlank String name,
        @NotNull SourceType type,
        @NotBlank String url,
        String category,
        Boolean enabled,
        @Min(0) @Max(1) Double reliabilityScore
) {
}
