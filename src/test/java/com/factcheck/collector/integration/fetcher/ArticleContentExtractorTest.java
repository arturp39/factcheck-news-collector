package com.factcheck.collector.integration.fetcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleContentExtractorTest {

    @Mock HttpClient httpClient;
    @Mock HttpResponse<InputStream> httpResponse;

    ArticleContentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ArticleContentExtractor();

        ReflectionTestUtils.setField(extractor, "userAgent", "TestAgent/1.0");
        ReflectionTestUtils.setField(extractor, "timeoutSeconds", 1);
        ReflectionTestUtils.setField(extractor, "maxHtmlBytes", 1024 * 1024);
        ReflectionTestUtils.setField(extractor, "minTextLength", 20);
        ReflectionTestUtils.setField(extractor, "warnCooldownMs", 0L);
        ReflectionTestUtils.setField(extractor, "hostBackoffMaxMs", 60_000L);

        // replace internal http client
        ReflectionTestUtils.setField(extractor, "httpClient", httpClient);
    }

    @Test
    void extractMainText_success_parsesArticleText() throws Exception {
        String html = """
                <html><body>
                  <article>
                    <p>This is a paragraph that is definitely longer than thirty characters.</p>
                    <p>Another paragraph that is definitely longer than thirty characters.</p>
                  </article>
                </body></html>
                """;

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        HttpHeaders headers = HttpHeaders.of(
                Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
                (k, v) -> true
        );
        when(httpResponse.headers()).thenReturn(headers);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));

        String text = extractor.extractMainText("https://example.com/a1");
        assertNotNull(text);
        assertTrue(text.contains("definitely longer"));
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void extractMainText_nonHtmlContentType_returnsNull() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        java.net.http.HttpHeaders headers = java.net.http.HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json")),
                (k, v) -> true
        );
        when(httpResponse.headers()).thenReturn(headers);

        assertNull(extractor.extractMainText("https://example.com/a2"));
    }



    @Test
    void extractMainText_429_setsHostBackoff_andSecondCallDoesNotHitNetwork() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        HttpHeaders headers = HttpHeaders.of(
                Map.of("Content-Type", List.of("text/html"), "Retry-After", List.of("0")),
                (k, v) -> true
        );
        when(httpResponse.headers()).thenReturn(headers);
        when(httpResponse.statusCode()).thenReturn(429);

        assertNull(extractor.extractMainText("https://rate-limited.example.com/a"));

        assertNull(extractor.extractMainText("https://rate-limited.example.com/b"));

        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}