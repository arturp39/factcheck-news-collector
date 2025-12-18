package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleContentService {

    private final ArticleRepository articleRepository;
    private final WeaviateIndexingService weaviateIndexingService;

    public ArticleContentResponse getArticleContent(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));

        // Pull stored chunks from Weaviate to rebuild the article body
        List<String> chunks = weaviateIndexingService.getChunksForArticle(articleId);

        if (chunks.isEmpty()) {
            log.warn("No chunks found in Weaviate for article id={}", articleId);
            throw new IllegalStateException("No content chunks found for article id=" + articleId);
        }

        String fullContent = String.join("\n\n", chunks);

        return ArticleContentResponse.builder()
                .articleId(article.getId())
                .sourceId(article.getSource().getId())
                .sourceName(article.getSource().getName())
                .externalUrl(article.getExternalUrl())
                .title(article.getTitle())
                .publishedDate(article.getPublishedDate())
                .content(fullContent)
                .build();
    }
}