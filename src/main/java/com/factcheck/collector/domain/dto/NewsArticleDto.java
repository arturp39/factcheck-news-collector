package com.factcheck.collector.domain.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class NewsArticleDto {
    private Long sourceId;
    private String sourceName;
    private String externalUrl;
    private String title;
    private String author;
    private String description;
    private String content;
    private Instant publishedAt;
}
