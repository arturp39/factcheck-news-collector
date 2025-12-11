package com.factcheck.collector.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SearchFilters {
    private List<String> sourceName;
    private LocalDateTime publishedAfter;
    private LocalDateTime publishedBefore;
}
