package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.exception.FetchException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RssFetcherTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetch_parsesEntriesAndUsesContentExtractor() throws Exception {
        String rss = """
                <rss version="2.0">
                  <channel>
                    <title>News</title>
                    <item>
                      <title>Item 1</title>
                      <link>%s/article-1</link>
                      <description>desc1</description>
                      <pubDate>Wed, 18 Dec 2024 10:00:00 GMT</pubDate>
                    </item>
                    <item>
                      <title>Item 2</title>
                      <link>%s/article-2</link>
                      <description></description>
                    </item>
                  </channel>
                </rss>
                """.formatted(baseUrl, baseUrl);

        server.createContext("/feed", exchange -> {
            byte[] bytes = rss.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        ArticleContentExtractor extractor = Mockito.mock(ArticleContentExtractor.class);
        when(extractor.extractMainText(anyString())).thenReturn("full text");

        RssFetcher fetcher = new RssFetcher(extractor);
        ReflectionTestUtils.setField(fetcher, "userAgent", "TestAgent/1.0");

        Source source = Source.builder()
                .id(1L)
                .type(SourceType.RSS)
                .url(baseUrl + "/feed")
                .build();

        List<RawArticle> articles = fetcher.fetch(source);

        assertThat(articles).hasSize(2);
        assertThat(articles.getFirst().getExternalUrl()).contains("/article-1");
        assertThat(articles.getFirst().getRawText()).isEqualTo("full text");
        assertThat(articles.get(1).getRawText()).isEqualTo("full text");
        assertThat(articles.getFirst().getPublishedDate()).isBefore(Instant.now().plusSeconds(60));
    }

    @Test
    void fetch_throwsOnHttpError() throws Exception {
        server.createContext("/feed", exchange -> exchange.sendResponseHeaders(500, -1));
        server.start();

        ArticleContentExtractor extractor = Mockito.mock(ArticleContentExtractor.class);
        RssFetcher fetcher = new RssFetcher(extractor);
        Source source = Source.builder()
                .id(2L)
                .type(SourceType.RSS)
                .url(baseUrl + "/feed")
                .build();

        ReflectionTestUtils.setField(fetcher, "userAgent", "TestAgent/1.0");

        assertThatThrownBy(() -> fetcher.fetch(source))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("Failed to fetch RSS from");
    }
}
