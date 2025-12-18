package com.factcheck.collector.service;

import com.factcheck.collector.dto.ChunkResult;
import com.factcheck.collector.exception.WeaviateException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeaviateIndexingServiceTest {

    @Test
    void searchByEmbeddingFiltersByScoreAndParsesResults() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer("/v1/graphql", 200, """
                {
                  "data": {
                    "Get": {
                      "ArticleChunk": [
                        {
                          "text": "keep",
                          "articleId": 1,
                          "articleUrl": "url1",
                          "articleTitle": "title1",
                          "sourceName": "source1",
                          "publishedDate": "2024-01-01T00:00:00",
                          "chunkIndex": 1,
                          "_additional": { "distance": 0.2 }
                        },
                        {
                          "text": "discard",
                          "articleId": 2,
                          "articleUrl": "url2",
                          "articleTitle": "title2",
                          "sourceName": "source2",
                          "publishedDate": "2024-01-01T00:00:00",
                          "chunkIndex": 2,
                          "_additional": { "distance": 0.9 }
                        }
                      ]
                    }
                  }
                }
                """, capturedBody);

        WeaviateIndexingService service = serviceWithBaseUrl(server, 10);

        List<ChunkResult> results = service.searchByEmbedding(
                List.of(0.1, 0.2),
                5,
                0.3f,
                "cid-1"
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).isEqualTo("keep");
        assertThat(capturedBody.get()).contains("nearVector");

        server.stop(0);
    }

    @Test
    void searchByEmbeddingThrowsOnHttpError() throws Exception {
        HttpServer server = startServer("/v1/graphql", 500, "error", new AtomicReference<>());
        WeaviateIndexingService service = serviceWithBaseUrl(server, 5);

        assertThatThrownBy(() -> service.searchByEmbedding(List.of(), 1, 0.0f, "cid"))
                .isInstanceOf(WeaviateException.class);

        server.stop(0);
    }

    @Test
    void getChunksForArticleRespectsLimitAndOrders() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer("/v1/graphql", 200, """
                {
                  "data": {
                    "Get": {
                      "ArticleChunk": [
                        { "text": "second", "chunkIndex": 1 },
                        { "text": "first", "chunkIndex": 0 },
                        { "text": "", "chunkIndex": 2 }
                      ]
                    }
                  }
                }
                """, capturedBody);

        WeaviateIndexingService service = serviceWithBaseUrl(server, 5);

        List<String> chunks = service.getChunksForArticle(9L);

        assertThat(chunks).containsExactly("first", "second");
        assertThat(capturedBody.get()).contains("limit: 5");

        server.stop(0);
    }

    private WeaviateIndexingService serviceWithBaseUrl(HttpServer server, int chunkLimit) throws Exception {
        WeaviateIndexingService service = new WeaviateIndexingService();
        setField(service, "baseUrl", "http://localhost:" + server.getAddress().getPort());
        setField(service, "articleChunkLimit", chunkLimit);
        return service;
    }

    private HttpServer startServer(String path, int status, String body, AtomicReference<String> capturedBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(requestBytes, StandardCharsets.UTF_8));
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
