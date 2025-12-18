package com.factcheck.collector.integration.weaviate;

import com.factcheck.collector.exception.WeaviateException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeaviateClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock HttpClient httpClient;
    @Mock HttpResponse<String> httpResponse;

    private WeaviateClient client;

    @BeforeEach
    void setUp() {
        client = new WeaviateClient(mapper, "http://weaviate.local///", 15);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
    }

    @Test
    void getSchema_success_returnsJson_andUsesStrippedBaseUrl() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"classes\":[]}");

        JsonNode node = client.getSchema();
        assertThat(node.path("classes").isArray()).isTrue();

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCap.capture(), any(HttpResponse.BodyHandler.class));

        URI uri = reqCap.getValue().uri();
        assertThat(uri.toString()).isEqualTo("http://weaviate.local/v1/schema");
        assertThat(reqCap.getValue().method()).isEqualTo("GET");
    }

    @Test
    void getSchema_non2xx_throwsWeaviateException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);

        assertThatThrownBy(() -> client.getSchema())
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Weaviate getSchema failed");

        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }


    @Test
    void createClass_non2xx_throwsWeaviateException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"error\":\"bad\"}");

        ObjectNode body = mapper.createObjectNode().put("class", "X");

        assertThatThrownBy(() -> client.createClass(body))
                .isInstanceOf(WeaviateException.class)
                .hasMessageContaining("createClass failed");
    }

    @Test
    void batchObjects_addsCorrelationIdHeader_whenProvided() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"result\":[]}");

        ObjectNode batch = mapper.createObjectNode();
        batch.putArray("objects");

        client.batchObjects(batch, "cid-123");

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCap.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest req = reqCap.getValue();
        assertThat(req.uri().toString()).isEqualTo("http://weaviate.local/v1/batch/objects");
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(req.headers().firstValue("X-Correlation-Id")).contains("cid-123");
        assertThat(req.bodyPublisher()).isPresent();
    }

    @Test
    void graphql_blankCorrelationId_doesNotAddHeader() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"data\":{}}");

        client.graphql("{ Get { Something } }", "   ");

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCap.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest req = reqCap.getValue();
        assertThat(req.uri().toString()).isEqualTo("http://weaviate.local/v1/graphql");
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.headers().firstValue("X-Correlation-Id")).isEmpty();
        assertThat(req.bodyPublisher()).isPresent();
    }
}
