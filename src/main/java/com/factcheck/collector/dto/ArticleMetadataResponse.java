package com.factcheck.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleMetadataResponse {
    private Long id;
    private Long sourceId;
    private String sourceName;
    private String externalUrl;
    private String title;
    private Instant publishedDate;
    private int chunkCount;
    private String status;
    private boolean weaviateIndexed;
}
