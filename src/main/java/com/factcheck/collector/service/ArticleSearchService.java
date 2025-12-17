package com.factcheck.collector.service;

import com.factcheck.collector.dto.SearchRequest;
import com.factcheck.collector.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSearchService {

    private final WeaviateIndexingService weaviateIndexingService;
    @Value("${search.embedding-dimension:768}")
    private int embeddingDimension;

    public SearchResponse search(SearchRequest request, String correlationId) {

        log.info("Search request received: limit={}, minScore={} correlationId={}",
                request.getLimit(), request.getMinScore(), correlationId);

        if (request.getEmbedding() == null || request.getEmbedding().size() != embeddingDimension) {
            throw new IllegalArgumentException("Embedding must have dimension " + embeddingDimension);
        }

        long start = System.currentTimeMillis();

        var results = weaviateIndexingService.searchByEmbedding(
                request.getEmbedding(),
                request.getLimit(),
                request.getMinScore(),
                correlationId
        );

        long duration = System.currentTimeMillis() - start;

        return SearchResponse.builder()
                .results(results)
                .totalFound(results.size())
                .executionTimeMs(duration)
                .correlationId(correlationId)
                .build();
    }
}