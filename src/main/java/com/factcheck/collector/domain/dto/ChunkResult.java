package com.factcheck.collector.domain.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResult {
    private String text;

    private long articleId;
    private String articleUrl;
    private String articleTitle;

    private String sourceName;
    private LocalDateTime publishedDate;

    private int chunkIndex;

    private float score;
}
