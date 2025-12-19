package com.factcheck.collector.dto;

import com.factcheck.collector.domain.enums.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SourceUpdateRequest(
        String name,
        SourceType type,
        String url,
        String category,
        Boolean enabled,
        @Min(0) @Max(1) Double reliabilityScore
) {
}
