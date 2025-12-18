package com.factcheck.collector.service;

import com.factcheck.collector.domain.dto.ChunkResult;
import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.exception.WeaviateException;
import com.factcheck.collector.integration.weaviate.WeaviateClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeaviateIndexingServiceTest {

    @Mock WeaviateClient weaviateClient;
    ObjectMapper mapper = new ObjectMapper();

    WeaviateIndexingService svc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        svc = new WeaviateIndexingService(weaviateClient, mapper);
    }

    @Test
    void ensureSchema_classExists_doesNotCreate() {
        ObjectNode schema = mapper.createObjectNode();
        ArrayNode classes = schema.putArray("classes");
        ObjectNode c = classes.addObject();
        c.put("class", "ArticleChunk");

        when(weaviateClient.getSchema()).thenReturn(schema);

        svc.ensureSchema();
        verify(weaviateClient, never()).createClass(any());
    }

    @Test
    void ensureSchema_missing_createsClass() {
        ObjectNode schema = mapper.createObjectNode();
        schema.putArray("classes"); // empty
        when(weaviateClient.getSchema()).thenReturn(schema);

        svc.ensureSchema();
        verify(weaviateClient).createClass(any(ObjectNode.class));
    }

    @Test
    void indexArticleChunks_mismatchSizes_throws() {
        Article a = Article.builder()
                .id(1L)
                .externalUrl("https://example.com/a")
                .title("T")
                .source(Source.builder().name("BBC").build())
                .publishedDate(Instant.now())
                .build();

        assertThrows(WeaviateException.class, () ->
                svc.indexArticleChunks(a, List.of("c1", "c2"), List.of(List.of(1.0)), "c"));
    }

    @Test
    void searchByEmbedding_filtersByMinScore_andSorts() {
        String respJson = """
                {
                  "data": { "Get": {
                    "ArticleChunk": [
                      { "text":"A", "articleId":1, "articleUrl":"u", "articleTitle":"t", "sourceName":"s",
                        "publishedDate":"2024-01-01T00:00:00Z", "chunkIndex":0,
                        "_additional": { "distance": 0.1 }
                      },
                      { "text":"B", "articleId":2, "articleUrl":"u2", "articleTitle":"t2", "sourceName":"s2",
                        "publishedDate":"2024-01-01T00:00:00Z", "chunkIndex":1,
                        "_additional": { "distance": 0.9 }
                      }
                    ]
                  } }
                }
                """;

        try {
            JsonNode node = mapper.readTree(respJson);
            when(weaviateClient.graphql(anyString(), any())).thenReturn(node);
        } catch (Exception e) {
            fail(e);
        }

        List<ChunkResult> out = svc.searchByEmbedding(List.of(0.1, 0.2), 10, 0.5f, "cid");
        assertEquals(1, out.size());
        assertEquals("A", out.get(0).getText());
        assertTrue(out.get(0).getScore() >= 0.5f);
    }

    @Test
    void getChunksForArticle_sortsAndFiltersBlank() throws Exception {
        String respJson = """
                {
                  "data": { "Get": {
                    "ArticleChunk": [
                      { "text":"c2", "chunkIndex":1 },
                      { "text":"", "chunkIndex":0 },
                      { "text":"c1", "chunkIndex":0 }
                    ]
                  } }
                }
                """;

        when(weaviateClient.graphql(anyString(), isNull()))
                .thenReturn(mapper.readTree(respJson));

        List<String> chunks = svc.getChunksForArticle(123);
        assertEquals(List.of("c1", "c2"), chunks);
    }
}