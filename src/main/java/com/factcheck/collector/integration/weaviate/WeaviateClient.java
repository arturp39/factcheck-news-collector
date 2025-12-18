package com.factcheck.collector.integration.weaviate;

import com.factcheck.collector.exception.WeaviateException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Slf4j
@Component
public class WeaviateClient {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    private final String baseUrl;

    public WeaviateClient(
            ObjectMapper mapper,
            @Value("${weaviate.base-url}") String baseUrl,
            @Value("${weaviate.timeout-seconds:15}") int timeoutSeconds
    ) {
        this.mapper = mapper;
        this.baseUrl = stripTrailingSlash(baseUrl);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public JsonNode getSchema() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/schema"))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new WeaviateException("Weaviate schema GET failed status=" + resp.statusCode(), null);
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            throw new WeaviateException("Weaviate getSchema failed", e);
        }
    }

    public void createClass(ObjectNode classBody) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/schema"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(classBody)))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate createClass error status={} body={}", resp.statusCode(), resp.body());
                throw new WeaviateException("Weaviate createClass failed status=" + resp.statusCode(), null);
            }
        } catch (Exception e) {
            throw new WeaviateException("Weaviate createClass failed", e);
        }
    }

    public JsonNode batchObjects(ObjectNode batchBody, String correlationId) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/batch/objects"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(batchBody)));

            if (correlationId != null && !correlationId.isBlank()) {
                b.header("X-Correlation-Id", correlationId);
            }

            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate batchObjects error status={} body={}", resp.statusCode(), resp.body());
                throw new WeaviateException("Weaviate batchObjects failed status=" + resp.statusCode(), null);
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            throw new WeaviateException("Weaviate batchObjects failed", e);
        }
    }

    public JsonNode graphql(String query, String correlationId) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("query", query);

            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/graphql"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));

            if (correlationId != null && !correlationId.isBlank()) {
                b.header("X-Correlation-Id", correlationId);
            }

            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Weaviate graphql error status={} body={}", resp.statusCode(), resp.body());
                throw new WeaviateException("Weaviate graphql failed status=" + resp.statusCode(), null);
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            throw new WeaviateException("Weaviate graphql failed", e);
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return null;
        String x = s.trim();
        while (x.endsWith("/")) x = x.substring(0, x.length() - 1);
        return x;
    }
}