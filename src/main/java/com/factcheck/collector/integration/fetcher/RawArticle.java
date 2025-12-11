package com.factcheck.collector.integration.fetcher;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class RawArticle {
    String externalUrl;
    String title;
    String description;
    String rawText;
    Instant publishedDate;
}
