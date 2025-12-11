package com.factcheck.collector.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChunkResult {
    private String text;
    private Long articleId;
    private String articleUrl;
    private String articleTitle;
    private String sourceName;
    private LocalDateTime publishedDate;
    private Integer chunkIndex;
    private Float score;
}
