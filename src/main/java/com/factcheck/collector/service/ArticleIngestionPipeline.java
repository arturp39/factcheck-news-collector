package com.factcheck.collector.service;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.exception.ProcessingFailedException;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIngestionPipeline {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;

    private final ArticleProcessingService articleProcessingService;
    private final EmbeddingService embeddingService;
    private final WeaviateIndexingService weaviateIndexingService;

    public int ingestAll(List<NewsArticleDto> dtos, UUID correlationId) {
        if (dtos == null || dtos.isEmpty()) return 0;

        int processedOk = 0;

        for (NewsArticleDto dto : dtos) {
            try {
                if (dto.getSourceId() == null) {
                    log.warn("Skipping article with no sourceId (url={})", dto.getExternalUrl());
                    continue;
                }

                Source source = sourceRepository.findById(dto.getSourceId())
                        .orElseThrow(() -> new IllegalStateException("Source not found id=" + dto.getSourceId()));

                String fullText = chooseText(dto);
                if (fullText == null || fullText.isBlank()) {
                    log.debug("Skipping article with no usable text url={}", dto.getExternalUrl());
                    continue;
                }

                Article article = Article.builder()
                        .source(source)
                        .externalUrl(dto.getExternalUrl())
                        .title(dto.getTitle())
                        .description(dto.getDescription())
                        .publishedDate(dto.getPublishedAt())
                        .status(ArticleStatus.PENDING)
                        .fetchedAt(Instant.now())
                        .build();

                try {
                    article = articleRepository.save(article);
                } catch (DataIntegrityViolationException dup) {
                    log.debug("Duplicate article (db) url={}, skipping", dto.getExternalUrl());
                    continue;
                }

                processAndIndexArticle(article, fullText, correlationId.toString());
                processedOk++;

            } catch (Exception e) {
                log.warn("Failed ingesting dto url={}: {}", dto.getExternalUrl(), e.toString(), e);
            }
        }

        return processedOk;
    }

    private String chooseText(NewsArticleDto dto) {
        if (dto.getContent() != null && !dto.getContent().isBlank()) return dto.getContent();
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) return dto.getDescription();
        return null;
    }

    private void processAndIndexArticle(Article article, String fullText, String correlationId) {
        article.setStatus(ArticleStatus.PROCESSING);
        articleRepository.save(article);

        try {
            var chunks = articleProcessingService.createChunks(article, fullText, correlationId);
            var embeddings = embeddingService.embedChunks(chunks, correlationId);

            weaviateIndexingService.indexArticleChunks(article, chunks, embeddings, correlationId);

            article.setChunkCount(chunks.size());
            article.setWeaviateIndexed(true);
            article.setStatus(ArticleStatus.PROCESSED);
            articleRepository.save(article);

        } catch (Exception e) {
            article.setStatus(ArticleStatus.FAILED);
            article.setErrorMessage(e.getMessage());
            articleRepository.save(article);
            throw new ProcessingFailedException(e);
        }
    }
}