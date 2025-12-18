package com.factcheck.collector.controller;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.ArticleStatus;
import com.factcheck.collector.dto.ArticleContentResponse;
import com.factcheck.collector.dto.ArticleMetadataResponse;
import com.factcheck.collector.dto.SearchRequest;
import com.factcheck.collector.dto.SearchResponse;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.service.ArticleContentService;
import com.factcheck.collector.service.ArticleSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalArticleController.class)
class InternalArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleRepository articleRepository;

    @MockitoBean
    private ArticleSearchService articleSearchService;

    @MockitoBean
    private ArticleContentService articleContentService;

    @Test
    void search_passesRequestAndCorrelationIdToService() throws Exception {
        SearchResponse mockResponse = SearchResponse.builder()
                .results(List.of())
                .totalFound(0)
                .executionTimeMs(5L)
                .correlationId("cid-1")
                .build();
        when(articleSearchService.search(any(), any())).thenReturn(mockResponse);

        String payload = """
                {
                  "embedding": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
                  %s
                  ],
                  "limit": 5,
                  "minScore": 0.7
                }
                """.formatted(",0.0".repeat(760)); // 768 dims in total

        mockMvc.perform(post("/internal/articles/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Correlation-ID", "cid-header"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFound").value(0));

        ArgumentCaptor<SearchRequest> reqCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<String> cidCaptor = ArgumentCaptor.forClass(String.class);

        verify(articleSearchService).search(reqCaptor.capture(), cidCaptor.capture());
        assertThat(cidCaptor.getValue()).isEqualTo("cid-header");
        assertThat(reqCaptor.getValue().getEmbedding()).hasSize(768);
    }

    @Test
    void getArticle_returnsMappedMetadataResponse() throws Exception {
        Source source = Source.builder()
                .id(10L)
                .name("Guardian")
                .build();

        Article article = Article.builder()
                .id(1L)
                .source(source)
                .externalUrl("https://example.com")
                .title("Title")
                .publishedDate(Instant.parse("2024-01-01T00:00:00Z"))
                .chunkCount(5)
                .status(ArticleStatus.PROCESSED)
                .weaviateIndexed(true)
                .build();

        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
    }

    @Test
    void getArticleContent_delegatesToService() throws Exception {
        ArticleContentResponse response = ArticleContentResponse.builder()
                .articleId(1L)
                .sourceId(10L)
                .sourceName("Guardian")
                .externalUrl("https://example.com")
                .title("Title")
                .content("Some content")
                .build();

        when(articleContentService.getArticleContent(1L)).thenReturn(response);

        mockMvc.perform(get("/internal/articles/{id}/content", 1L)
                        .header("X-Correlation-ID", "cid-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(1L))
                .andExpect(jsonPath("$.content").value("Some content"));

        verify(articleContentService).getArticleContent(1L);
    }

    @Test
    void getArticle_returnsBadRequestWhenMissing() throws Exception {
        when(articleRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/articles/{id}", 99L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void search_returnsBadRequestForWrongEmbeddingSize() throws Exception {
        String payload = """
                {
                  "embedding": [0.1, 0.2],
                  "limit": 5,
                  "minScore": 0.7
                }
                """;

        when(articleSearchService.search(any(), any())).thenThrow(new IllegalArgumentException("Embedding must have dimension"));

        mockMvc.perform(post("/internal/articles/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
