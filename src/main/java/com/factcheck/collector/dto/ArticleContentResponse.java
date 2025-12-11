package com.factcheck.collector.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ArticleContentResponse {

    private Long articleId;
    private Long sourceId;
    private String sourceName;

    private String externalUrl;
    private String title;
    private Instant publishedDate;
    private String content;
}
