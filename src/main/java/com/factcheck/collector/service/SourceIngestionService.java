package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.exception.ProcessingFailedException;
import com.factcheck.collector.integration.fetcher.RawArticle;
import com.factcheck.collector.integration.fetcher.SourceFetcher;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceIngestionService {

    private final SourceRepository sourceRepository;
    private final ArticleRepository articleRepository;
    private final IngestionLogRepository ingestionLogRepository;
    private final List<SourceFetcher> fetchers;
    private final ArticleProcessingService articleProcessingService;
    private final EmbeddingService embeddingService;
    private final WeaviateIndexingService weaviateIndexingService;

    public void ingestSingleSource(Source source, String correlationId) {

        log.info("Ingesting source id={} name={} correlationId={}",
                source.getId(), source.getName(), correlationId);

        IngestionLog logEntry = IngestionLog.builder()
                .source(source)
                .status(IngestionStatus.RUNNING)
                .correlationId(correlationId)
                .startedAt(Instant.now())
                .build();
        ingestionLogRepository.save(logEntry);

        int fetched = 0;
        int processed = 0;
        int failed = 0;

        try {
            SourceFetcher fetcher = fetchers.stream()
                    .filter(f -> f.supports(source.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No fetcher for type " + source.getType()));

            List<RawArticle> rawArticles = fetcher.fetch(source);
            fetched = rawArticles.size();

            for (RawArticle raw : rawArticles) {
                final String url = raw.getExternalUrl();

                try {
                    if (isNonTextMediaPage(url)) {
                        log.info("Skipping non-text media page: {}", url);
                        continue;
                    }

                    String fullText = raw.getRawText();
                    if (fullText == null || fullText.isBlank()) {
                        log.info("Skipping article with no extracted text: {}", url);
                        continue;
                    }

                    if (articleRepository.findByExternalUrl(url).isPresent()) {
                        log.debug("Article already exists, skipping url={}", url);
                        continue;
                    }

                    Article article = Article.builder()
                            .source(source)
                            .externalUrl(url)
                            .title(raw.getTitle())
                            .description(raw.getDescription())
                            .publishedDate(raw.getPublishedDate())
                            .status(ArticleStatus.PENDING)
                            .build();

                    try {
                        article = articleRepository.save(article);

                    } catch (DataIntegrityViolationException ex) {
                        log.info("Duplicate article detected at DB level, skipping url={}", url);
                        continue;
                    }

                    processAndIndexArticle(article, fullText, correlationId);
                    processed++;

                } catch (ProcessingFailedException e) {
                    failed++;
                    log.warn("Processing failed for article url={}", url, e);

                } catch (Exception e) {
                    failed++;
                    log.error("Unexpected error processing article url={}", url, e);
                }
            }

            logEntry.setStatus(
                    failed == 0 ? IngestionStatus.SUCCESS :
                            (processed > 0 ? IngestionStatus.PARTIAL : IngestionStatus.FAILED)
            );

            logEntry.setArticlesFetched(fetched);
            logEntry.setArticlesProcessed(processed);
            logEntry.setArticlesFailed(failed);
            logEntry.setCompletedAt(Instant.now());

            if (failed == 0) {
                source.setLastSuccessAt(Instant.now());
                source.setFailureCount(0);
            } else {
                source.setFailureCount(source.getFailureCount() + 1);
            }

            source.setLastFetchedAt(Instant.now());
            sourceRepository.save(source);

        } catch (FetchException fetchEx) {
            log.error("Failed to fetch source id={} name={}", source.getId(), source.getName(), fetchEx);
            logEntry.setStatus(IngestionStatus.FAILED);
            logEntry.setErrorDetails("Fetch error: " + fetchEx.getMessage());
            logEntry.setCompletedAt(Instant.now());
        }

        ingestionLogRepository.save(logEntry);
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
            log.error("Processing/indexing failed for article id={}", article.getId(), e);
            article.setStatus(ArticleStatus.FAILED);
            article.setErrorMessage(e.getMessage());
            articleRepository.save(article);
            throw new ProcessingFailedException(e);
        }
    }

    private boolean isNonTextMediaPage(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase();

        return u.contains("/video/")
                || u.contains("/videos/")
                || u.contains("/newsfeed/")
                || u.contains("/latest-news-bulletin")
                || u.contains("/picture/")
                || u.contains("/cartoon/")
                || u.contains("/gallery/")
                || u.contains("/slideshow/")
                || u.contains("/watch/")
                || u.contains("/live/")
                || u.contains("/iplayer/");
    }
}