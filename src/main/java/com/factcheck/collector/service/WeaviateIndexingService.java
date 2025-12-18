package com.factcheck.collector.service;

import com.factcheck.collector.domain.dto.ChunkResult;
import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.exception.WeaviateException;
import com.factcheck.collector.integration.weaviate.WeaviateClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeaviateIndexingService {

    private static final String CLASS_NAME = "ArticleChunk";

    private final WeaviateClient weaviateClient;
    private final ObjectMapper mapper;

    public void ensureSchema() {
        JsonNode schema = weaviateClient.getSchema();

        boolean hasClass = false;
        JsonNode classes = schema.path("classes");
        if (classes.isArray()) {
            for (JsonNode c : classes) {
                if (CLASS_NAME.equalsIgnoreCase(c.path("class").asText())) {
                    hasClass = true;
                    break;
                }
            }
        }

        if (hasClass) {
            log.info("Weaviate schema ok: class {} exists", CLASS_NAME);
            return;
        }

        log.info("Weaviate schema missing: creating class {}", CLASS_NAME);

        ObjectNode body = mapper.createObjectNode();
        body.put("class", CLASS_NAME);
        body.put("description", "Small article fragment for fact-checking");
        body.put("vectorizer", "none");

        ArrayNode props = body.putArray("properties");
        props.add(prop("text", "text"));
        props.add(prop("articleId", "int"));
        props.add(prop("articleUrl", "text"));
        props.add(prop("articleTitle", "text"));
        props.add(prop("sourceName", "text"));
        props.add(prop("publishedDate", "date"));
        props.add(prop("chunkIndex", "int"));

        weaviateClient.createClass(body);
    }

    public void indexArticleChunks(
            Article article,
            List<String> chunks,
            List<List<Double>> embeddings,
            String correlationId
    ) {
        if (chunks == null || chunks.isEmpty()) return;

        if (embeddings == null || chunks.size() != embeddings.size()) {
            throw new WeaviateException(
                    "Chunks size " + (chunks == null ? 0 : chunks.size()) +
                            " != embeddings size " + (embeddings == null ? 0 : embeddings.size()),
                    null
            );
        }

        ArrayNode objects = mapper.createArrayNode();

        for (int i = 0; i < chunks.size(); i++) {
            ObjectNode obj = mapper.createObjectNode();
            obj.put("class", CLASS_NAME);

            ObjectNode props = obj.putObject("properties");
            props.put("text", chunks.get(i));
            props.put("articleId", article.getId());
            props.put("articleUrl", article.getExternalUrl());
            props.put("articleTitle", article.getTitle());
            props.put("sourceName", article.getSource().getName());

            Instant published = article.getPublishedDate() != null ? article.getPublishedDate() : Instant.now();
            props.put("publishedDate", published.toString());

            props.put("chunkIndex", i);

            ArrayNode vector = mapper.createArrayNode();
            for (Double v : embeddings.get(i)) {
                vector.add(v);
            }
            obj.set("vector", vector);

            objects.add(obj);
        }

        ObjectNode batchBody = mapper.createObjectNode();
        batchBody.set("objects", objects);

        JsonNode resp = weaviateClient.batchObjects(batchBody, correlationId);

        JsonNode result = resp.path("result");
        if (result.isArray()) {
            for (JsonNode r : result) {
                JsonNode errors = r.path("errors");
                if (errors.has("error") && errors.path("error").isArray() && errors.path("error").size() > 0) {
                    log.warn("Weaviate batch object errors: {}", errors);
                }
            }
        }
    }

    public List<ChunkResult> searchByEmbedding(
            List<Double> embedding,
            int limit,
            float minScore,
            String correlationId
    ) {
        try {
            if (embedding == null || embedding.isEmpty()) return List.of();
            int lim = Math.max(1, limit);

            String vectorJson = mapper.writeValueAsString(embedding);

            String gql = String.format(
                    Locale.US,
                    "{ Get { %s(nearVector: {vector: %s}, limit: %d) " +
                            "{ text articleId articleUrl articleTitle sourceName publishedDate chunkIndex _additional { distance } } } }",
                    CLASS_NAME,
                    vectorJson,
                    lim
            );

            JsonNode resp = weaviateClient.graphql(gql, correlationId);

            JsonNode errors = resp.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                log.warn("Weaviate GraphQL errors: {}", errors);
            }

            JsonNode data = resp.path("data").path("Get").path(CLASS_NAME);

            List<ChunkResult> results = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode n : data) {
                    double distance = n.path("_additional").path("distance").asDouble(1.0);
                    float score = 1f - (float) distance;
                    if (score < minScore) continue;

                    String text = n.path("text").asText("");
                    long articleId = n.path("articleId").asLong();
                    String url = n.path("articleUrl").asText("");
                    String title = n.path("articleTitle").asText("");
                    String sourceName = n.path("sourceName").asText("");
                    int chunkIndex = n.path("chunkIndex").asInt();

                    LocalDateTime published = parseWeaviateDateToLocalDateTime(n.path("publishedDate").asText(null));

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

            results.sort(Comparator.comparingDouble((ChunkResult r) -> -r.getScore()));
            return results;

        } catch (Exception e) {
            throw new WeaviateException("Weaviate search failed", e);
        }
    }

    public List<String> getChunksForArticle(long articleId) {
        try {
            int limit = 512;

            String gql = String.format(
                    Locale.US,
                    "{ Get { %s(where: { path: [\"articleId\"], operator: Equal, valueInt: %d }, " +
                            "limit: %d, sort: [{ path: [\"chunkIndex\"], order: asc }]) { text chunkIndex } } }",
                    CLASS_NAME,
                    articleId,
                    limit
            );

            JsonNode resp = weaviateClient.graphql(gql, null);

            JsonNode errors = resp.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                log.warn("Weaviate GraphQL errors in getChunksForArticle: {}", errors);
            }

            JsonNode data = resp.path("data").path("Get").path(CLASS_NAME);
            if (!data.isArray()) return List.of();

            List<ChunkWithIndex> list = new ArrayList<>();
            for (JsonNode n : data) {
                String text = n.path("text").asText("");
                int idx = n.path("chunkIndex").asInt(0);
                if (text == null || text.isBlank()) continue;
                list.add(new ChunkWithIndex(idx, text));
            }

            list.sort(Comparator.comparingInt(ChunkWithIndex::chunkIndex));

            List<String> chunks = new ArrayList<>(list.size());
            for (ChunkWithIndex c : list) chunks.add(c.text());
            return chunks;

        } catch (Exception e) {
            throw new WeaviateException("Weaviate getChunksForArticle failed", e);
        }
    }

    private ObjectNode prop(String name, String dataType) {
        ObjectNode p = mapper.createObjectNode();
        p.put("name", name);
        ArrayNode dt = p.putArray("dataType");
        dt.add(dataType);
        return p;
    }

    private record ChunkWithIndex(int chunkIndex, String text) {}

    private static LocalDateTime parseWeaviateDateToLocalDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            Instant inst = Instant.parse(iso);
            return LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(iso.replace("Z", ""));
            } catch (Exception ignored2) {
                return null;
            }
        }
    }
}