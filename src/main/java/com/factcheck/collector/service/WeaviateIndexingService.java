package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.dto.ChunkResult;
import com.factcheck.collector.exception.WeaviateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeaviateIndexingService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${weaviate.base-url}")
    private String baseUrl;

    private static final String CLASS_NAME = "ArticleChunk";

    public void ensureSchema() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/schema"))
                    .GET()
                    .build();

            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode root = mapper.readTree(resp.body());
            JsonNode classes = root.path("classes");

            boolean hasClass = false;
            if (classes.isArray()) {
                for (JsonNode c : classes) {
                    if (CLASS_NAME.equalsIgnoreCase(c.path("class").asText())) {
                        hasClass = true;
                        break;
                    }
                }
            }

            if (!hasClass) {
                log.info("Creating Weaviate class {}", CLASS_NAME);
                String body = """
                        {
                          "class": "ArticleChunk",
                          "description": "Small article fragment for fact-checking",
                          "vectorizer": "none",
                          "properties": [
                            { "name": "text",   "dataType": ["text"] },
                            { "name": "articleId", "dataType": ["int"] },
                            { "name": "articleUrl", "dataType": ["text"] },
                            { "name": "articleTitle", "dataType": ["text"] },
                            { "name": "sourceName", "dataType": ["text"] },
                            { "name": "publishedDate", "dataType": ["date"] },
                            { "name": "chunkIndex", "dataType": ["int"] }
                          ]
                        }
                        """;

                HttpRequest createReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/schema"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
            }
        } catch (Exception e) {
            log.error("ensureSchema() failed", e);
        }
    }

    public void indexArticleChunks(
            Article article,
            List<String> chunks,
            List<List<Double>> embeddings,
            String correlationId
    ) {
        if (chunks.isEmpty()) {
            return;
        }
        if (chunks.size() != embeddings.size()) {
            throw new WeaviateException(
                    "Chunks size " + chunks.size() + " != embeddings size " + embeddings.size(),
                    null
            );
        }

        try {
            log.info("Indexing {} chunks for article id={} into Weaviate", chunks.size(), article.getId());
            List<String> objects = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                String vectorJson = mapper.writeValueAsString(embeddings.get(i));

                String obj = """
                        {
                          "class": "ArticleChunk",
                          "properties": {
                            "text": %s,
                            "articleId": %d,
                            "articleUrl": %s,
                            "articleTitle": %s,
                            "sourceName": %s,
                            "publishedDate": %s,
                            "chunkIndex": %d
                          },
                          "vector": %s
                        }
                        """.formatted(
                        mapper.writeValueAsString(chunks.get(i)),
                        article.getId(),
                        mapper.writeValueAsString(article.getExternalUrl()),
                        mapper.writeValueAsString(article.getTitle()),
                        mapper.writeValueAsString(article.getSource().getName()),
                        mapper.writeValueAsString(article.getPublishedDate() != null
                                ? article.getPublishedDate().toString()
                                : Instant.now().toString()),
                        i,
                        vectorJson
                );

                objects.add(obj);
            }

            String batchBody = """
                    {
                      "objects": [
                        %s
                      ]
                    }
                    """.formatted(String.join(",", objects));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/batch/objects"))
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .POST(HttpRequest.BodyPublishers.ofString(batchBody))
                    .build();

            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate batch error status={} body={}", resp.statusCode(), resp.body());
                throw new WeaviateException("Weaviate batch error " + resp.statusCode(), null);
            }
        } catch (Exception e) {
            throw new WeaviateException("Failed to index into Weaviate", e);
        }
    }

    public List<ChunkResult> searchByEmbedding(
            List<Double> embedding,
            int limit,
            float minScore,
            String correlationId
    ) {
        try {
            String gql = buildSearchQuery(embedding, limit);

            var root = mapper.createObjectNode();
            root.put("query", gql);
            String body = mapper.writeValueAsString(root);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/graphql"))
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode respRoot = mapper.readTree(resp.body());
            JsonNode errors = respRoot.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                log.warn("Weaviate GraphQL errors: {}", errors);
            }

            JsonNode data = respRoot.path("data").path("Get").path("ArticleChunk");
            List<ChunkResult> results = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode n : data) {
                    double distance = n.path("_additional").path("distance").asDouble(1.0);
                    float score = 1f - (float) distance;
                    if (score < minScore) {
                        continue;
                    }

                    String text = n.path("text").asText("");
                    long articleId = n.path("articleId").asLong();
                    String url = n.path("articleUrl").asText("");
                    String title = n.path("articleTitle").asText("");
                    String sourceName = n.path("sourceName").asText("");
                    String publishedIso = n.path("publishedDate").asText(null);

                    LocalDateTime published = null;
                    if (publishedIso != null && !publishedIso.isBlank()) {
                        published = LocalDateTime.parse(publishedIso.replace("Z", ""));
                    }

                    int chunkIndex = n.path("chunkIndex").asInt();

                    results.add(ChunkResult.builder()
                            .text(text)
                            .articleId(articleId)
                            .articleUrl(url)
                            .articleTitle(title)
                            .sourceName(sourceName)
                            .publishedDate(published)
                            .chunkIndex(chunkIndex)
                            .score(score)
                            .build());
                }
            }

            return results;
        } catch (Exception e) {
            throw new WeaviateException("Weaviate search failed", e);
        }
    }

    public List<String> getChunksForArticle(long articleId) {
        try {
            int limit = 512;

            String gql = String.format(
                    """
                    {
                      Get {
                        ArticleChunk(
                          where: {
                            path: ["articleId"]
                            operator: Equal
                            valueInt: %d
                          },
                          limit: %d,
                          sort: [{ path: ["chunkIndex"], order: asc }]
                        ) {
                          text
                          chunkIndex
                        }
                      }
                    }
                    """,
                    articleId,
                    limit
            );

            var root = mapper.createObjectNode();
            root.put("query", gql);
            String body = mapper.writeValueAsString(root);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/graphql"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate articleChunks query error status={} body={}",
                        resp.statusCode(), resp.body());
                throw new WeaviateException("Weaviate articleChunks error " + resp.statusCode(), null);
            }

            JsonNode respRoot = mapper.readTree(resp.body());
            JsonNode errors = respRoot.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                log.warn("Weaviate GraphQL errors in getChunksForArticle: {}", errors);
            }

            JsonNode data = respRoot.path("data").path("Get").path("ArticleChunk");
            List<ChunkWithIndex> list = new ArrayList<>();

            if (data.isArray()) {
                for (JsonNode n : data) {
                    String text = n.path("text").asText("");
                    int idx = n.path("chunkIndex").asInt(0);
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    list.add(new ChunkWithIndex(idx, text));
                }
            }

            list.sort(java.util.Comparator.comparingInt(ChunkWithIndex::chunkIndex));

            List<String> chunks = new ArrayList<>(list.size());
            for (ChunkWithIndex c : list) {
                chunks.add(c.text());
            }
            return chunks;

        } catch (Exception e) {
            throw new WeaviateException("Weaviate getChunksForArticle failed", e);
        }
    }

    private record ChunkWithIndex(int chunkIndex, String text) {}

    private String buildSearchQuery(List<Double> embedding, int limit) throws Exception {
        String vectorJson = mapper.writeValueAsString(embedding);

        return String.format(
                java.util.Locale.US,
                "{ Get { ArticleChunk(nearVector: {vector: %s}, limit: %d) " +
                        "{ text articleId articleUrl articleTitle sourceName publishedDate chunkIndex _additional { distance } } } }",
                vectorJson,
                limit
        );
    }
}