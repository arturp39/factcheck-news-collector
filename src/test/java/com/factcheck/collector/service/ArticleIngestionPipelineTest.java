package com.factcheck.collector.service;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleIngestionPipelineTest {

    @Mock ArticleRepository articleRepository;
    @Mock SourceRepository sourceRepository;
    @Mock ArticleProcessingService articleProcessingService;
    @Mock EmbeddingService embeddingService;
    @Mock WeaviateIndexingService weaviateIndexingService;

    @Test
    void ingestAll_skipsNullOrEmptyInput() {
        ArticleIngestionPipeline p = new ArticleIngestionPipeline(
                articleRepository, sourceRepository,
                articleProcessingService, embeddingService, weaviateIndexingService
        );

        assertEquals(0, p.ingestAll(null, UUID.randomUUID()));
        assertEquals(0, p.ingestAll(List.of(), UUID.randomUUID()));
        verifyNoInteractions(articleRepository, sourceRepository);
    }

    @Test
    void ingestAll_happyPath_processesAndIndexes() {
        ArticleIngestionPipeline p = new ArticleIngestionPipeline(
                articleRepository, sourceRepository,
                articleProcessingService, embeddingService, weaviateIndexingService
        );

        Source src = Source.builder().id(7L).name("BBC").build();
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(src));

        NewsArticleDto dto = new NewsArticleDto();
        dto.setSourceId(7L);
        dto.setExternalUrl("https://example.com/a");
        dto.setTitle("A");
        dto.setContent("FULL");
        dto.setPublishedAt(Instant.now());

        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> {
            Article a = inv.getArgument(0);

            // simulate DB assigning an id on first save
            try {
                var f = Article.class.getDeclaredField("id");
                f.setAccessible(true);
                Object current = f.get(a);
                if (current == null) {
                    f.set(a, 100L);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return a;
        });

        when(articleProcessingService.createChunks(any(), eq("FULL"), anyString()))
                .thenReturn(List.of("c1", "c2"));
        when(embeddingService.embedChunks(eq(List.of("c1", "c2")), anyString()))
                .thenReturn(List.of(List.of(0.1), List.of(0.2)));

        int ok = p.ingestAll(List.of(dto), UUID.randomUUID());
        assertEquals(1, ok);

        verify(weaviateIndexingService).indexArticleChunks(any(Article.class), eq(List.of("c1","c2")), anyList(), anyString());
        verify(articleRepository, atLeast(2)).save(any(Article.class)); // PENDING -> PROCESSING -> PROCESSED
    }

    @Test
    void ingestAll_duplicateDb_skips() {
        ArticleIngestionPipeline p = new ArticleIngestionPipeline(
                articleRepository, sourceRepository,
                articleProcessingService, embeddingService, weaviateIndexingService
        );

        Source src = Source.builder().id(7L).name("BBC").build();
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(src));

        NewsArticleDto dto = new NewsArticleDto();
        dto.setSourceId(7L);
        dto.setExternalUrl("https://example.com/a");
        dto.setTitle("A");
        dto.setContent("FULL");

        when(articleRepository.save(any(Article.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        int ok = p.ingestAll(List.of(dto), UUID.randomUUID());
        assertEquals(0, ok);

        verifyNoInteractions(articleProcessingService, embeddingService, weaviateIndexingService);
    }

    @Test
    void ingestAll_processingFailure_marksFailedAndContinues() {
        ArticleIngestionPipeline p = new ArticleIngestionPipeline(
                articleRepository, sourceRepository,
                articleProcessingService, embeddingService, weaviateIndexingService
        );

        Source src = Source.builder().id(7L).name("BBC").build();
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(src));

        NewsArticleDto dto = new NewsArticleDto();
        dto.setSourceId(7L);
        dto.setExternalUrl("https://example.com/a");
        dto.setTitle("A");
        dto.setContent("FULL");

        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));

        when(articleProcessingService.createChunks(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        int ok = p.ingestAll(List.of(dto), UUID.randomUUID());
        assertEquals(0, ok);

        verify(articleRepository, atLeast(2)).save(any(Article.class));
    }
}