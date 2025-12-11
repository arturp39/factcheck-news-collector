package com.factcheck.collector.config;

import com.factcheck.collector.service.WeaviateIndexingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WeaviateConfig {

    private final WeaviateIndexingService weaviateIndexingService;

    @PostConstruct
    public void initSchema() {
        log.info("Initializing Weaviate schema (ArticleChunk class) if missing");
        weaviateIndexingService.ensureSchema();
    }
}
