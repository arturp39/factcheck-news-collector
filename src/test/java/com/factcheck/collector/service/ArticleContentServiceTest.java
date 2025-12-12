package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleContentServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private WeaviateIndexingService weaviateIndexingService;

    @InjectMocks
    private ArticleContentService articleContentService;

    @Test
    void getArticleContentBuildsResponseFromChunks() {
        Source source = Source.builder()
                .id(5L)
                .name("Demo Source")
                .build();

        Article article = Article.builder()
                .id(10L)
                .source(source)
                .externalUrl("https://example.com/article")
                .title("An Article")
                .publishedDate(Instant.parse("2024-01-02T03:04:05Z"))
                .build();

        when(articleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(weaviateIndexingService.getChunksForArticle(10L)).thenReturn(List.of("First", "Second"));

        ArticleContentResponse response = articleContentService.getArticleContent(10L);

        assertThat(response.getArticleId()).isEqualTo(10L);
        assertThat(response.getSourceId()).isEqualTo(5L);
        assertThat(response.getSourceName()).isEqualTo("Demo Source");
        assertThat(response.getExternalUrl()).isEqualTo("https://example.com/article");
        assertThat(response.getTitle()).isEqualTo("An Article");
        assertThat(response.getPublishedDate()).isEqualTo(Instant.parse("2024-01-02T03:04:05Z"));
        assertThat(response.getContent()).isEqualTo("First\n\nSecond");
    }

    @Test
    void getArticleContentThrowsWhenArticleMissing() {
        when(articleRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleContentService.getArticleContent(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Article not found");
    }

    @Test
    void getArticleContentThrowsWhenChunksMissing() {
        Source source = Source.builder().id(3L).name("No Chunks Source").build();
        Article article = Article.builder().id(7L).source(source).title("Title").externalUrl("url").build();

        when(articleRepository.findById(7L)).thenReturn(Optional.of(article));
        when(weaviateIndexingService.getChunksForArticle(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> articleContentService.getArticleContent(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No content chunks");
    }
}